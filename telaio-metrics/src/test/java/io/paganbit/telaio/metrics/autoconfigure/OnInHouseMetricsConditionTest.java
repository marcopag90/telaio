package io.paganbit.telaio.metrics.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the decision matrix of {@link OnInHouseMetricsCondition}: the in-house path is on by
 * default, stands aside when the Micrometer recorder takes over, and an explicit
 * {@code telaio.metrics.in-house.enabled} always wins.
 */
class OnInHouseMetricsConditionTest {

    private static final ClassLoader WITH_MICROMETER = OnInHouseMetricsConditionTest.class.getClassLoader();
    private static final ClassLoader WITHOUT_MICROMETER = new FilteredClassLoader(MeterRegistry.class);

    @Test
    void byDefault_shouldEnableInHouse() {
        assertThat(matches(new MockEnvironment(), WITH_MICROMETER)).isTrue();
    }

    @Test
    void micrometerEnabledWithMicrometerOnClasspath_shouldDisableInHouse() {
        MockEnvironment env = new MockEnvironment()
            .withProperty(OnInHouseMetricsCondition.MICROMETER_ENABLED, "true");
        assertThat(matches(env, WITH_MICROMETER)).isFalse();
    }

    @Test
    void micrometerEnabledButMicrometerAbsentFromClasspath_shouldKeepInHouse() {
        MockEnvironment env = new MockEnvironment()
            .withProperty(OnInHouseMetricsCondition.MICROMETER_ENABLED, "true");
        assertThat(matches(env, WITHOUT_MICROMETER)).isTrue();
    }

    @Test
    void explicitInHouseTrue_shouldWinEvenWhenMicrometerActive() {
        MockEnvironment env = new MockEnvironment()
            .withProperty(OnInHouseMetricsCondition.MICROMETER_ENABLED, "true")
            .withProperty(OnInHouseMetricsCondition.IN_HOUSE_ENABLED, "true");
        assertThat(matches(env, WITH_MICROMETER)).isTrue();
    }

    @Test
    void explicitInHouseFalse_shouldWinEvenWithoutMicrometer() {
        MockEnvironment env = new MockEnvironment()
            .withProperty(OnInHouseMetricsCondition.IN_HOUSE_ENABLED, "false");
        assertThat(matches(env, WITH_MICROMETER)).isFalse();
    }

    private static boolean matches(MockEnvironment environment, ClassLoader classLoader) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        when(context.getClassLoader()).thenReturn(classLoader);
        return new OnInHouseMetricsCondition().matches(context, mock(AnnotatedTypeMetadata.class));
    }
}
