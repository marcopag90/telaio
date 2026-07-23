package com.paganbit.telaio.rest.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SingleConnectionConditionTest {

    private final SingleConnectionCondition condition = new SingleConnectionCondition();
    private final AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);

    private boolean matches(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return condition.getMatchOutcome(context, metadata).isMatch();
    }

    @Test
    void matchesWhenExactlyOneConnectionIsConfigured() {
        assertThat(matches(Map.of(
            "telaio.rest-client.connections.billing.base-url", "https://billing.example.com")))
            .isTrue();
    }

    @Test
    void matchesWhenOneConnectionIsNamedDefaultAmongSeveral() {
        assertThat(matches(Map.of(
            "telaio.rest-client.connections.default.base-url", "https://a.example.com",
            "telaio.rest-client.connections.billing.base-url", "https://b.example.com")))
            .isTrue();
    }

    @Test
    void doesNotMatchWithSeveralConnectionsAndNoneNamedDefault() {
        assertThat(matches(Map.of(
            "telaio.rest-client.connections.billing.base-url", "https://a.example.com",
            "telaio.rest-client.connections.inventory.base-url", "https://b.example.com")))
            .isFalse();
    }

    @Test
    void doesNotMatchWhenNoConnectionIsConfigured() {
        assertThat(matches(Map.of())).isFalse();
    }
}
