package com.paganbit.telaio.showcase.it;

import com.paganbit.telaio.showcase.dal.setting.AppSetting;
import com.paganbit.telaio.showcase.dal.setting.AppSettingDalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of an <em>internal</em> DAL ({@code app-settings},
 * {@link AppSettingDalService}). An internal DAL must be fully usable in-process while being entirely
 * absent from every remote boundary:
 * <ul>
 *     <li>it has no REST endpoint — requests return {@code 404}, indistinguishable from a DAL that does
 *     not exist, so its presence is not even revealed;</li>
 *     <li>it is omitted from the generated OpenAPI document;</li>
 *     <li>yet it is still registered and injectable, and the seeded data is reachable programmatically.</li>
 * </ul>
 */
class InternalDalIT extends AbstractShowcaseIT {

    @Autowired
    private AppSettingDalService appSettings;

    @Test
    void internalDal_isNotExposedOverRest() {
        // Authenticated, so a 404 cannot be confused with a 401 from the security filter chain.
        assertThat(list(ADMIN, "app-settings", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getOne(ADMIN, "app-settings", "feature.beta-search.enabled").getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void internalDal_isNotDocumentedInOpenApi() {
        ResponseEntity<String> response = exchange(
            null, HttpMethod.GET, "http://localhost:%d/v3/api-docs/TELAIO".formatted(port), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode paths = tree(response).get("paths");
        assertThat(paths.has("/dal/v1/app-settings")).isFalse();
        assertThat(paths.has("/dal/v1/app-settings/{id}")).isFalse();
    }

    @Test
    void internalDal_isUsableProgrammatically() {
        Optional<AppSetting> setting = appSettings.readOne("feature.beta-search.enabled");

        assertThat(setting).isPresent();
        assertThat(setting.get().getValue()).isEqualTo("false");
    }
}
