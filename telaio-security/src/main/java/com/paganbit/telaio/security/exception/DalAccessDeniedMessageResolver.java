package com.paganbit.telaio.security.exception;

/**
 * Strategy for resolving operation-specific access denied messages.
 * <p>
 * Implementations generate user-facing (or API-facing) denial messages for DAL
 * authorization failures, allowing consistent wording and localization per
 * operation type.
 * </p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalAccessDeniedMessageResolver {

    /**
     * Resolves the denial message for a create operation.
     *
     * @param dalName the logical DAL/resource name
     * @return the access denied message for create
     */
    String forCreate(String dalName);

    /**
     * Resolves the denial message for a read/list operation.
     *
     * @param dalName the logical DAL/resource name
     * @return the access denied message for read/list
     */
    String forRead(String dalName);

    /**
     * Resolves the denial message for a read-one operation.
     *
     * @param dalName the logical DAL/resource name
     * @param id      the identifier of the target resource
     * @return the access denied message for read-one
     */
    String forReadOne(String dalName, Object id);

    /**
     * Resolves the denial message for an update operation.
     *
     * @param dalName the logical DAL/resource name
     * @param id      the identifier of the target resource
     * @return the access denied message for update
     */
    String forUpdate(String dalName, Object id);

    /**
     * Resolves the denial message for a delete operation.
     *
     * @param dalName the logical DAL/resource name
     * @param id      the identifier of the target resource
     * @return the access denied message for delete
     */
    String forDelete(String dalName, Object id);
}
