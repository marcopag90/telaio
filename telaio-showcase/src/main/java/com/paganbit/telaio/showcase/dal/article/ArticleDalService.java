package com.paganbit.telaio.showcase.dal.article;

import com.turkraft.springfilter.parser.node.FilterNode;
import com.paganbit.telaio.audit.annotation.DalAudit;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.jpa.JpaDal;
import com.paganbit.telaio.security.DalSecurityContextHelper;
import com.paganbit.telaio.showcase.role.UserRole;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;

import static com.paganbit.telaio.introspection.PropertyNameResolver.propertyName;

/**
 * <h2>Use case — a read-only resource via per-operation exposure, with audit and an implicit read filter</h2>
 * <p>
 * Exposes the {@link Article} entity as a <b>read-only</b> REST resource, leaving field-level RBAC at its
 * default (no filtering):
 * <ul>
 *   <li><b>Read-only by exposure</b> — {@code @DalService(operations = {READ, READ_ONE})} publishes only
 *       the two read operations. Create/update/delete are <em>structurally</em> absent: they are omitted
 *       from the OpenAPI document and a write request is rejected before reaching the DAL. Because the
 *       collection and item URIs still answer their read sibling, a write returns {@code 405 Method Not
 *       Allowed}. This states "this resource has no writes" more honestly than documenting write endpoints
 *       that deny everyone at runtime. For identity-aware authorization (writes allowed for some principals,
 *       denied with an auditable {@code 403} for others) see {@code bulletins}.</li>
 *   <li><b>Field-level RBAC</b> is left at the default (no field hidden). See {@code product} and
 *       {@code employee} for the two RBAC strategies.</li>
 *   <li><b>Audit</b> via {@code @DalAudit} (every exposed operation recorded).</li>
 *   <li><b>Metrics</b> are <b>ON by default</b>, so no annotation is needed; latency and counts are collected.</li>
 * </ul>
 *
 * <h3>Implicit default filter</h3>
 * Overriding {@link #defaultFilter()} injects a query constraint that is AND-combined with the caller's
 * own {@code q} filter on <em>every</em> read (see {@code AbstractDal#combineWithDefaultFilter}). Here it
 * implements row-level visibility: non-power-users (anyone who is not {@code DEVELOPER}/{@code ADMIN}) can
 * only ever see {@code PUBLISHED} articles, regardless of the filter they send. Returning {@code null}
 * disables the constraint for power-users, who see drafts and archived articles too.
 */
@DalService(name = "articles", operations = {DalOperationType.READ, DalOperationType.READ_ONE})
@DalAudit(operations = {DalOperationType.READ, DalOperationType.READ_ONE})
public class ArticleDalService extends JpaDal<Article, Long> {

    @Override
    protected @Nullable FilterNode defaultFilter() {
        Authentication auth = DalSecurityContextHelper.getCurrentAuthentication();
        boolean isPowerUser = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> UserRole.DEVELOPER.equals(a) || UserRole.ADMIN.equals(a));
        if (isPowerUser) {
            return null;
        }
        return filterBuilder.field(propertyName(Article::getStatus))
            .equal(filterBuilder.input(ArticleStatus.PUBLISHED))
            .get();
    }
}
