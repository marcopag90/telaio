package com.paganbit.telaio.showcase.dal.setting;

import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.jpa.JpaDal;

/**
 * <h2>Use case — an internal DAL</h2>
 * <p>
 * Exposes nothing. {@link AppSetting} is application configuration used by other services in-process;
 * it must not become a public REST resource. Marking the DAL {@code internal = true} keeps every
 * channel-agnostic feature that wraps the DAL bean — validation, transactions, metrics, audit,
 * bean-level RBAC — while removing it from the web boundary entirely:
 * <ul>
 *     <li>no REST endpoint is assembled, so {@code GET /dal/v1/app-settings} returns {@code 404}
 *     (its very existence is not revealed);</li>
 *     <li>it is omitted from the generated OpenAPI document, so it never appears in Swagger UI.</li>
 * </ul>
 * <p>
 * Other beans inject this DAL (or {@code AppSettingRepository}) and use it programmatically.
 */
@DalService(name = "app-settings", internal = true)
public class AppSettingDalService extends JpaDal<AppSetting, String> {
}
