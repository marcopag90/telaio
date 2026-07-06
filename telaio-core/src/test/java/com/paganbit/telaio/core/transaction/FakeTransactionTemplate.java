package com.paganbit.telaio.core.transaction;

import org.jspecify.annotations.Nullable;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.Mockito.spy;

public class FakeTransactionTemplate extends TransactionTemplate {

    public static FakeTransactionTemplate spied() {
        return spy(new FakeTransactionTemplate());
    }

    @Override
    @Nullable
    public <T> T execute(TransactionCallback<T> action) throws TransactionException {
        return action.doInTransaction(new SimpleTransactionStatus());
    }
}
