package io.paganbit.telaio.metrics.autoconfigure;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * Matches when the in-house metrics pipeline (aggregator, flush scheduler, store, and
 * {@code telaiometrics} endpoint) should be active.
 *
 * <p>The in-house path is on by default but stands aside when the Micrometer recorder takes over,
 * so a measurement is never aggregated twice. The rule is:</p>
 * <ul>
 *   <li>if {@code telaio.metrics.in-house.enabled} is set explicitly, honor it (lets an operator
 *       force both paths on for a transition, or both off);</li>
 *   <li>otherwise the in-house path is enabled unless the Micrometer recorder is active —
 *       {@code telaio.metrics.micrometer.enabled=true} <em>and</em> Micrometer is on the classpath.</li>
 * </ul>
 *
 * <p>The classpath guard means a stray {@code micrometer.enabled=true} without the Micrometer
 * library still leaves the in-house path running rather than disabling all metrics.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class OnInHouseMetricsCondition implements Condition {

    static final String IN_HOUSE_ENABLED = "telaio.metrics.in-house.enabled";
    static final String MICROMETER_ENABLED = "telaio.metrics.micrometer.enabled";
    static final String METER_REGISTRY_CLASS = "io.micrometer.core.instrument.MeterRegistry";

    @Override
    public boolean matches(ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
        String explicit = context.getEnvironment().getProperty(IN_HOUSE_ENABLED);
        if (explicit != null) {
            return Boolean.parseBoolean(explicit);
        }
        boolean micrometerRequested = Boolean.parseBoolean(
            context.getEnvironment().getProperty(MICROMETER_ENABLED, "false"));
        boolean micrometerOnClasspath =
            ClassUtils.isPresent(METER_REGISTRY_CLASS, context.getClassLoader());
        return !(micrometerRequested && micrometerOnClasspath);
    }
}
