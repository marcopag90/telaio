package com.paganbit.telaio.rest.client.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Matches when one connection is unambiguously primary: it is the only one configured, or it is
 * named {@value TelaioRestClientProperties#DEFAULT_CONNECTION_NAME}. Gates the convenience
 * {@code TelaioClient} bean; otherwise clients are resolved through the
 * {@code TelaioClientRegistry}.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
class SingleConnectionCondition extends SpringBootCondition {

    private static final String CONNECTIONS_PROPERTY = TelaioRestClientProperties.PREFIX + ".connections";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Set<String> names = Binder.get(context.getEnvironment())
            .bind(CONNECTIONS_PROPERTY, Bindable.mapOf(String.class, TelaioRestClientProperties.Connection.class))
            .map(Map::keySet)
            .orElse(Set.of());
        ConditionMessage.Builder message = ConditionMessage.forCondition("Telaio primary connection");
        if (Objects.requireNonNull(names).size() == 1) {
            return ConditionOutcome.match(message.foundExactly("connection '" + names.iterator().next() + "'"));
        }
        if (names.contains(TelaioRestClientProperties.DEFAULT_CONNECTION_NAME)) {
            return ConditionOutcome.match(message.foundExactly(
                "connection '" + TelaioRestClientProperties.DEFAULT_CONNECTION_NAME + "'"));
        }
        return ConditionOutcome.noMatch(message.didNotFind(
            "a single connection or one named '"
                + TelaioRestClientProperties.DEFAULT_CONNECTION_NAME + "'").atAll());
    }
}
