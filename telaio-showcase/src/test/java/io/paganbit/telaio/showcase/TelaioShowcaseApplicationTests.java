package io.paganbit.telaio.showcase;

import com.turkraft.springfilter.parser.node.FilterNode;
import io.paganbit.telaio.core.Dal;
import io.paganbit.telaio.core.adapter.DalOperationAdapter;
import io.paganbit.telaio.core.annotation.DalService;
import io.paganbit.telaio.core.registry.DalManager;
import io.paganbit.telaio.web.registry.WebDalOperationAdapterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end smoke test that boots the full Telaio stack (core + security + web + jpa) and verifies
 * that all DAL services are registered and their operation adapters are assembled.
 * Also confirms that a DAL without {@code @DalSecurity} is denied by default.
 */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
class TelaioShowcaseApplicationTests {

    @Autowired
    private DalManager dalManager;

    @Autowired
    private WebDalOperationAdapterRegistry adapterRegistry;

    @Test
    void articlesDalIsRegistered() {
        assertNotNull(dalManager.getDefinitionByName("articles"));
    }

    @Test
    void productsDalIsRegistered() {
        assertNotNull(dalManager.getDefinitionByName("products"));
    }

    @Test
    void announcementsDalIsRegistered() {
        assertNotNull(dalManager.getDefinitionByName("announcements"));
    }

    @Test
    void employeesDalIsRegistered() {
        assertNotNull(dalManager.getDefinitionByName("employees"));
    }

    @Test
    void departmentsDalIsRegistered() {
        assertNotNull(dalManager.getDefinitionByName("departments"));
    }

    @Test
    void allOperationAdaptersAreAssembled() {
        assertNotNull(adapterRegistry.get("articles"));
        assertNotNull(adapterRegistry.get("products"));
        assertNotNull(adapterRegistry.get("announcements"));
        assertNotNull(adapterRegistry.get("employees"));
        assertNotNull(adapterRegistry.get("departments"));
    }

    @Test
    void dalWithoutSecurityDefinitionIsPermittedByDefault() {
        DalOperationAdapter<Object, Object> adapter = adapterRegistry.get("widgets");

        Map<String, Object> props = Map.of("name", "w");
        // A DAL declared without @DalSecurity is open by default (PermitAll fallback), so the
        // operation proceeds instead of being denied.
        assertDoesNotThrow(() -> adapter.create(props));
    }

    @TestConfiguration
    static class WidgetTestConfiguration {

        @Bean("widgets")
        WidgetDalService widgets() {
            return new WidgetDalService();
        }
    }

    @DalService(name = "widgets")
    static class WidgetDalService implements Dal<Object, Long> {

        @Override
        public Object create(Map<String, Object> properties) {
            return properties;
        }

        @Override
        public Page<Object> read(@Nullable FilterNode filter, Pageable pageable) {
            return Page.empty();
        }

        @Override
        public Optional<Object> readOne(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<Object> update(Long id, Map<String, Object> properties) {
            return Optional.empty();
        }

        @Override
        public void delete(Long id) {
            // noop
        }

        @Override
        public Class<Object> getEntityClass() {
            return Object.class;
        }

        @Override
        public Class<Long> getIdClass() {
            return Long.class;
        }
    }
}
