package com.paganbit.telaio.showcase.dal.announcement;

import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.jpa.JpaDal;
import com.paganbit.telaio.metrics.annotation.DalMetrics;

/**
 * <h2>Use case — the simplest possible DAL</h2>
 * <p>
 * Exposes the {@link Announcement} entity as a full CRUD REST resource with <em>no</em> cross-cutting
 * concern enabled:
 * <ul>
 *   <li><b>No security</b> — there is no {@code @DalSecurity} annotation. A DAL declared without it is
 *       open by default (the security interceptor falls back to {@code PermitAllDalAuthAdapter} and
 *       {@code NoopDalRbacAdapter}), so every operation is allowed and no field is hidden. Authentication
 *       is still enforced by the application's {@code SecurityConfiguration}, but the DAL itself applies
 *       no authorization or field-level RBAC.</li>
 *   <li><b>No audit</b> — auditing is opt-in, so the absence of {@code @DalAudit} means nothing is recorded.</li>
 *   <li><b>No metrics</b> — metrics are <b>ON by default</b> for every DAL, so disabling them is the one
 *       thing this example must do explicitly via {@code @DalMetrics(enabled = false)}.</li>
 * </ul>
 * <p>
 * This is the baseline a developer gets from {@code @DalService} alone: declare the annotation, point it at
 * a {@link com.paganbit.telaio.jpa.JpaDalRepository} for the entity, and a complete REST API is generated.
 */
@DalService(name = "announcements")
@DalMetrics(enabled = false)
public class AnnouncementDalService extends JpaDal<Announcement, Long> {
}
