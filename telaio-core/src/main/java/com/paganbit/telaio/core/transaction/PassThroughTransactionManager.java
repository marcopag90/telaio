package com.paganbit.telaio.core.transaction;

import org.jspecify.annotations.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * No-op {@link PlatformTransactionManager}: callbacks run without transactional semantics.
 *
 * <p>Fallback for environments where no real transaction manager is available. Every DAL requires
 * a transaction manager; this implementation satisfies that contract while leaving each operation
 * non-transactional. Declare a real {@link PlatformTransactionManager} to restore full
 * transactional semantics.</p>
 *
 * @author Marco Pagan
 * @since 2.0.0
 */
public class PassThroughTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(@Nullable TransactionDefinition definition) {
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
        // no-op
    }

    @Override
    public void rollback(TransactionStatus status) {
        // no-op
    }
}