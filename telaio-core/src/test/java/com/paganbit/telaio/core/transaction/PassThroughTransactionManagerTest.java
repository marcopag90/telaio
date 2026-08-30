package com.paganbit.telaio.core.transaction;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PassThroughTransactionManager}: the status is usable, commit/rollback are
 * true no-ops, and a {@code TransactionTemplate} built on it executes callbacks transparently.
 */
class PassThroughTransactionManagerTest {

    private final PassThroughTransactionManager manager = new PassThroughTransactionManager();

    @Test
    void getTransaction_returnsUsableStatus() {
        TransactionStatus status = manager.getTransaction(new DefaultTransactionDefinition());

        assertThat(status).isNotNull();
        assertThat(status.isCompleted()).isFalse();
    }

    @Test
    void getTransaction_acceptsNullDefinition() {
        assertThat(manager.getTransaction(null)).isNotNull();
    }

    @Test
    void commitAndRollback_areNoOps() {
        TransactionStatus status = manager.getTransaction(new DefaultTransactionDefinition());

        assertThatCode(() -> {
            manager.commit(status);
            manager.rollback(status);
        }).doesNotThrowAnyException();
    }

    @Test
    void transactionTemplate_executesCallbackAndReturnsResult() {
        TransactionTemplate template = new TransactionTemplate(manager);

        String result = template.execute(status -> "result");

        assertThat(result).isEqualTo("result");
    }

    @Test
    void transactionTemplate_propagatesCallbackException() {
        TransactionTemplate template = new TransactionTemplate(manager);

        assertThatIllegalStateException()
            .isThrownBy(() -> template.executeWithoutResult(status -> {
                throw new IllegalStateException("boom");
            }))
            .withMessage("boom");
    }
}