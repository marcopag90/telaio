package com.paganbit.telaio.showcase.dal.bulletin;

import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.jpa.JpaDal;
import com.paganbit.telaio.metrics.annotation.DalMetrics;
import com.paganbit.telaio.security.annotation.DalSecurity;

/**
 * <h2>Use case — identity-aware CRUD authorization</h2>
 * <p>
 * Exposes the {@link Bulletin} entity as a full CRUD REST resource, but authorizes writes per principal:
 * everyone may read, only {@code ADMIN} may create/update/delete. This is the authorization layer's real
 * job and the complement to per-operation exposure:
 * <ul>
 *   <li><b>Exposure</b> (see {@code articles}, {@code feed}) decides which operations <em>exist</em> on
 *       the boundary — identity-independent, reflected in OpenAPI, enforced with {@code 404/405}.</li>
 *   <li><b>Authorization</b> (here, via {@code @DalSecurity} and {@link AdminWritesDalAuthAdapter})
 *       decides whether <em>this principal</em> may perform an exposed operation — evaluated per request,
 *       enforced with an auditable {@code 403}, while the endpoint stays documented.</li>
 * </ul>
 * The write endpoints are therefore present in the contract and return {@code 403} to non-admins, rather
 * than being absent as they would be under exposure.
 */
@DalService(name = "bulletins")
@DalSecurity(authAdapterClass = AdminWritesDalAuthAdapter.class)
@DalMetrics(enabled = false)
public class BulletinDalService extends JpaDal<Bulletin, Long> {
}
