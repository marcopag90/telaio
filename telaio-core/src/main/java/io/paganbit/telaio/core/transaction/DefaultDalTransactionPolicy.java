package io.paganbit.telaio.core.transaction;

import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Default transaction policy using required propagation and standard isolation.
 * Read operations are marked as read-only.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DefaultDalTransactionPolicy implements DalTransactionPolicy {

    @Override
    public DefaultTransactionDefinition forCreate() {
        return defaultWriteTransactionDefinition();
    }

    @Override
    public DefaultTransactionDefinition forRead() {
        return defaultReadOnlyTransactionDefinition();
    }

    @Override
    public DefaultTransactionDefinition forReadOne() {
        return defaultReadOnlyTransactionDefinition();
    }

    @Override
    public DefaultTransactionDefinition forUpdate() {
        return defaultWriteTransactionDefinition();
    }

    @Override
    public DefaultTransactionDefinition forDelete() {
        return defaultWriteTransactionDefinition();
    }

    protected DefaultTransactionDefinition defaultReadOnlyTransactionDefinition() {
        final var defaultTransactionDefinition = new DefaultTransactionDefinition();
        defaultTransactionDefinition.setReadOnly(true);
        return defaultTransactionDefinition;
    }

    protected DefaultTransactionDefinition defaultWriteTransactionDefinition() {
        final var defaultTransactionDefinition = new DefaultTransactionDefinition();
        defaultTransactionDefinition.setReadOnly(false);
        return defaultTransactionDefinition;
    }
}
