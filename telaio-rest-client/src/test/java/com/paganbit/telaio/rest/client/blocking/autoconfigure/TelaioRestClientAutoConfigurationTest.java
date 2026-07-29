package com.paganbit.telaio.rest.client.blocking.autoconfigure;

import com.paganbit.telaio.rest.client.DalPageRequest;
import com.paganbit.telaio.rest.client.blocking.TelaioClient;
import com.paganbit.telaio.rest.client.blocking.TelaioClientRegistry;
import com.paganbit.telaio.rest.client.blocking.TelaioRestClientCustomizer;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FieldNode;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TelaioRestClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TelaioRestClientAutoConfiguration.class));

    @Test
    void registryIsAlwaysRegisteredEvenWithoutConnections() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TelaioClientRegistry.class);
            assertThat(context).doesNotHaveBean(TelaioClient.class);
        });
    }

    @Test
    void backsOffWithoutRestClientOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader(RestClient.class))
            .run(context -> {
                assertThat(context).doesNotHaveBean(TelaioClientRegistry.class);
                assertThat(context).doesNotHaveBean(TelaioClient.class);
            });
    }

    @Test
    void singleConnectionExposesPrimaryClient() {
        runner.withPropertyValues("telaio.rest-client.connections.main.base-url=http://remote")
            .run(context -> {
                assertThat(context).hasSingleBean(TelaioClientRegistry.class);
                assertThat(context).hasSingleBean(TelaioClient.class);
                assertThat(context.getBean(TelaioClient.class))
                    .isSameAs(context.getBean(TelaioClientRegistry.class).get("main"));
            });
    }

    @Test
    void multipleConnectionsWithoutDefaultExposeOnlyTheRegistry() {
        runner.withPropertyValues(
                "telaio.rest-client.connections.billing.base-url=http://billing",
                "telaio.rest-client.connections.inventory.base-url=http://inventory")
            .run(context -> {
                assertThat(context).hasSingleBean(TelaioClientRegistry.class);
                assertThat(context).doesNotHaveBean(TelaioClient.class);
                assertThat(context.getBean(TelaioClientRegistry.class).get("billing")).isNotNull();
                assertThat(context.getBean(TelaioClientRegistry.class).get("inventory")).isNotNull();
            });
    }

    @Test
    void defaultNamedConnectionBacksThePrimaryClientAmongMany() {
        runner.withPropertyValues(
                "telaio.rest-client.connections.default.base-url=http://main",
                "telaio.rest-client.connections.billing.base-url=http://billing")
            .run(context -> {
                assertThat(context).hasSingleBean(TelaioClient.class);
                assertThat(context.getBean(TelaioClient.class))
                    .isSameAs(context.getBean(TelaioClientRegistry.class).get("default"));
            });
    }

    @Test
    void unknownConnectionFailsWithDescriptiveMessage() {
        runner.withPropertyValues("telaio.rest-client.connections.main.base-url=http://remote")
            .run(context -> {
                TelaioClientRegistry registry = context.getBean(TelaioClientRegistry.class);
                assertThatThrownBy(() -> registry.get("nope"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("telaio.rest-client.connections.nope.base-url");
            });
    }

    @Test
    void missingBaseUrlFailsLazilyWithDescriptiveMessage() {
        // Two connections, no 'default': no eager primary bean, so the failure surfaces on get().
        runner.withPropertyValues(
                "telaio.rest-client.connections.main.default-headers.X-A[0]=1",
                "telaio.rest-client.connections.other.base-url=http://other")
            .run(context -> {
                TelaioClientRegistry registry = context.getBean(TelaioClientRegistry.class);
                assertThatThrownBy(() -> registry.get("main"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("telaio.rest-client.connections.main.base-url");
            });
    }

    @Test
    void blankBaseUrlFailsLazilyWithDescriptiveMessage() {
        runner.withPropertyValues(
                "telaio.rest-client.connections.main.base-url=  ",
                "telaio.rest-client.connections.other.base-url=http://other")
            .run(context -> {
                TelaioClientRegistry registry = context.getBean(TelaioClientRegistry.class);
                assertThatThrownBy(() -> registry.get("main"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("telaio.rest-client.connections.main.base-url");
            });
    }

    @Test
    void defaultHeadersAreAppliedToOutgoingRequests() {
        AtomicReference<@Nullable MockRestServiceServer> serverRef = new AtomicReference<>();
        runner.withBean(TelaioRestClientCustomizer.class,
                () -> (name, builder) -> serverRef.set(MockRestServiceServer.bindTo(builder).build()))
            .withPropertyValues(
                "telaio.rest-client.connections.main.base-url=http://remote",
                "telaio.rest-client.connections.main.default-headers.X-Tenant[0]=acme")
            .run(context -> {
                TelaioClientRegistry registry = context.getBean(TelaioClientRegistry.class);
                TelaioClient client = registry.get("main");
                MockRestServiceServer server = Objects.requireNonNull(serverRef.get());
                server.expect(requestTo("http://remote/dal/v1/products/1"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("X-Tenant", "acme"))
                    .andRespond(withSuccess("""
                        {"id": 1}""", MediaType.APPLICATION_JSON));

                assertThat(client.dal("products", Product.class, Long.class).readOne(1L))
                    .contains(new Product(1L));
                server.verify();
            });
    }

    @Test
    void userProvidedFilterStringConverterRendersTheFilterTrees() {
        FilterStringConverter converter = mock(FilterStringConverter.class);
        when(converter.convert(any(FilterNode.class))).thenReturn("custom");
        AtomicReference<@Nullable MockRestServiceServer> serverRef = new AtomicReference<>();
        runner.withBean(FilterStringConverter.class, () -> converter)
            .withBean(TelaioRestClientCustomizer.class,
                () -> (name, builder) -> serverRef.set(MockRestServiceServer.bindTo(builder).build()))
            .withPropertyValues("telaio.rest-client.connections.main.base-url=http://remote")
            .run(context -> {
                TelaioClient client = context.getBean(TelaioClientRegistry.class).get("main");
                MockRestServiceServer server = Objects.requireNonNull(serverRef.get());
                server.expect(requestTo("http://remote/dal/v1/products?q=custom&page=0&size=10"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("""
                        {"content": [], "page": {"size": 10, "number": 0, "totalElements": 0,
                         "totalPages": 0}}""", MediaType.APPLICATION_JSON));

                client.dal("products", Product.class, Long.class)
                    .read(new FieldNode("ignored"), DalPageRequest.of(0, 10));
                server.verify();
            });
    }

    @Test
    void userProvidedObjectMapperBacksTheBuiltClients() {
        runner.withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withPropertyValues("telaio.rest-client.connections.main.base-url=http://remote")
            .run(context -> {
                assertThat(context).hasSingleBean(TelaioClient.class);
                assertThat(context.getBean(TelaioClientRegistry.class).get("main")).isNotNull();
            });
    }

    @Test
    void userDefinedBeansBackOffTheAutoConfiguration() {
        runner.withUserConfiguration(UserBeans.class)
            .withPropertyValues("telaio.rest-client.connections.main.base-url=http://remote")
            .run(context -> {
                assertThat(context.getBean(TelaioClientRegistry.class)).isSameAs(UserBeans.REGISTRY);
                assertThat(context.getBean(TelaioClient.class)).isSameAs(UserBeans.CLIENT);
            });
    }

    @Test
    void customizersAreAppliedPerConnectionWithItsName() {
        List<String> seen = new ArrayList<>();
        runner.withBean(TelaioRestClientCustomizer.class,
                () -> (name, builder) -> seen.add(name))
            .withPropertyValues(
                "telaio.rest-client.connections.billing.base-url=http://billing",
                "telaio.rest-client.connections.inventory.base-url=http://inventory")
            .run(context -> {
                TelaioClientRegistry registry = context.getBean(TelaioClientRegistry.class);
                registry.get("billing");
                registry.get("inventory");
                registry.get("billing"); // cached: no second customization
                assertThat(seen).containsExactly("billing", "inventory");
            });
    }

    record Product(@Nullable Long id) {
    }

    @Configuration(proxyBeanMethods = false)
    static class UserBeans {

        static final TelaioClientRegistry REGISTRY = mock(TelaioClientRegistry.class);
        static final TelaioClient CLIENT = mock(TelaioClient.class);

        @Bean
        TelaioClientRegistry customRegistry() {
            return REGISTRY;
        }

        @Bean
        TelaioClient customClient() {
            return CLIENT;
        }
    }
}
