package com.paganbit.telaio.showcase.dal.ticket;

import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.jpa.JpaDal;
import com.paganbit.telaio.jpa.JpaDalRepository;
import com.paganbit.telaio.rest.client.blocking.TelaioClient;
import com.paganbit.telaio.rest.client.blocking.v1.DalClient;
import jakarta.persistence.EntityManager;

import java.util.Map;
import java.util.Objects;

/**
 * <h2>Use case — a DAL that calls another DAL over the remote {@code TelaioClient} (round-trip)</h2>
 * <p>
 * Exposes the {@link SupportTicket} entity with full CRUD and, on every write, publishes an activity
 * entry to the append-only {@code feed} DAL <em>through the REST client</em> — a genuine HTTP
 * round-trip back to this same application ({@code POST /dal/v1/feed}), authenticated with basic auth
 * by a {@link com.paganbit.telaio.rest.client.blocking.TelaioRestClientCustomizer}. It demonstrates
 * the client configured at compile time (connection {@code self} under
 * {@code telaio.rest-client.connections}) and used as a first-class collaborator inside a DAL.
 * <ul>
 *   <li><b>create</b> → local persist, then a remote {@code feed} create ("Ticket opened: …").</li>
 *   <li><b>update</b> → local merge-patch, then a remote {@code feed} create ("Ticket … updated").
 *       The {@code feed} DAL is append-only, so the activity trail is always a <em>create</em>.</li>
 * </ul>
 *
 * <h3>Transactional publication</h3>
 * The publication is wired through the {@link com.paganbit.telaio.core.AbstractDal} lifecycle hooks
 *  * {@code finalizeAfterCreate}/{@code finalizeAfterUpdate}, which run <em>inside</em> the write's
 *  * transaction: if the remote call fails, the unchecked
 *  * {@link com.paganbit.telaio.rest.client.exception.DalClientException} propagates and the local
 *  * transaction is rolled back, so a failed activity write undoes the ticket write.
 *  * <p>
 *  * This is a <b>dual-write, not a distributed transaction</b>: the guarantee is one-directional. Only
 *  * a <em>failing</em> remote call rolls the local write back; if the remote call succeeds and the
 *  * local transaction then fails at commit, the already-committed {@code feed} entry is not undone (an
 * orphaned activity record).
 *
 * <h3>Not production-ready as-is</h3>
 * A teaching example — do not copy verbatim into a real service. Besides the dual-write above: the
 * synchronous self-call runs while the DB transaction is open, holding a connection for the whole
 * round-trip (against a real remote a self-call can starve the connection pool under load — size
 * pools and timeouts accordingly, or publish out of band); and every call carries a single static
 * service credential (see {@code ShowcaseRestClientConfig}) with no propagation of the originating
 * caller's identity.
 */
@DalService(name = "tickets")
public class SupportTicketDalService extends JpaDal<SupportTicket, Long> {

    private final DalClient<FeedActivity, Long> feed;

    public SupportTicketDalService(
        JpaDalRepository<SupportTicket, Long> repository,
        EntityManager entityManager,
        TelaioClient telaioClient
    ) {
        this.feed = Objects.requireNonNull(telaioClient, "telaioClient must not be null")
            .dal("feed", FeedActivity.class, Long.class);
        super(repository, entityManager);
    }

    @Override
    protected void finalizeAfterCreate(SupportTicket entity) {
        publishActivity("Ticket opened: " + entity.getSubject());
    }

    @Override
    protected void finalizeAfterUpdate(SupportTicket entity) {
        publishActivity("Ticket " + entity.getId() + " updated");
    }

    private void publishActivity(String message) {
        feed.create(Map.of("source", "tickets", "message", message));
    }
}
