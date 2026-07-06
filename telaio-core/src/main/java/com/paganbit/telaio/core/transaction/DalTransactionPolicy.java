package com.paganbit.telaio.core.transaction;

import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Strategy for providing transaction definitions per DAL operation type.
 * <p>
 * Implementations can customize settings such as propagation behavior,
 * isolation level, timeout, and read-only flags for each CRUD operation.
 * </p>
 * <p>
 * Returned definitions are used by DAL execution infrastructure to establish
 * operation-specific transaction boundaries.
 * </p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalTransactionPolicy {

    /**
     * Provides the transaction definition for create operations.
     *
     * @return the transaction definition to apply to create execution
     */
    DefaultTransactionDefinition forCreate();

    /**
     * Provides the transaction definition for read/list operations.
     *
     * @return the transaction definition to apply to read/list execution
     */
    DefaultTransactionDefinition forRead();

    /**
     * Provides the transaction definition for read-one operations.
     *
     * @return the transaction definition to apply to single-entity reads
     */
    DefaultTransactionDefinition forReadOne();

    /**
     * Provides the transaction definition for update operations.
     *
     * @return the transaction definition to apply to update execution
     */
    DefaultTransactionDefinition forUpdate();

    /**
     * Provides the transaction definition for delete operations.
     *
     * @return the transaction definition to apply to delete execution
     */
    DefaultTransactionDefinition forDelete();
}
