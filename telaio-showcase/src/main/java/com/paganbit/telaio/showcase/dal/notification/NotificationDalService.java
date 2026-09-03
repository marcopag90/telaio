package com.paganbit.telaio.showcase.dal.notification;

import com.paganbit.telaio.audit.annotation.DalAudit;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.mongo.MongoDal;

/**
 * <h2>Use case — a MongoDB-backed DAL next to the JPA ones</h2>
 * <p>
 * Exposes the {@link Notification} document as a full CRUD REST resource on the very same
 * {@code /dal/v1} surface as the PostgreSQL-backed DALs of this application. Extending
 * {@link MongoDal} plugs in Spring Data MongoDB; the {@code @DalService} registration, Bean Validation,
 * {@code q=} filtering (converted to a Mongo {@code $expr} query), paging, OpenAPI generation and
 * security defaults are provided by the framework unchanged.
 * <ul>
 *   <li><b>Two backends, two transaction managers:</b> the PostgreSQL DALs use Boot's
 *       {@code JpaTransactionManager}; this DAL receives the {@code MongoTransactionManager} declared in
 *       {@code MongoConfiguration} through the qualified {@code telaioMongoTransactionManager} bean, so
 *       every operation runs in a real multi-document transaction (the docker-compose service is a
 *       single-node replica set for that reason).</li>
 *   <li><b>Channel-agnostic cross-cutting features:</b> {@code @DalAudit} and the default-on metrics attach
 *       to the {@code Dal} bean regardless of the store — the metric buckets of this DAL even land in
 *       the PostgreSQL JDBC store.</li>
 *   <li><b>String id + {@code @Version}:</b> the recommended identifier type for Mongo entities, and
 *       optimistic locking with a version-checked delete (stale delete → {@code 409 Conflict}).</li>
 * </ul>
 * No security annotation: like {@code announcements}, the DAL is open to every authenticated user.
 */
@DalService(name = "notifications")
@DalAudit
public class NotificationDalService extends MongoDal<Notification, String> {
}
