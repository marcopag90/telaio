package io.paganbit.telaio.security.exception;

/**
 * Default {@link DalAccessDeniedMessageResolver} implementation.
 * <p>
 * Produces straightforward English access-denied messages that include the DAL
 * name and, where applicable, the target entity identifier.
 * </p>
 * <p>
 * This implementation is intended as a sensible baseline and can be replaced
 * when custom wording, localization, or structured error payload support is
 * required.
 * </p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DefaultDalAccessDeniedMessageResolver implements DalAccessDeniedMessageResolver {

    @Override
    public String forCreate(String dalName) {
        return "Not authorized to create entity in DAL [%s]".formatted(dalName);
    }

    @Override
    public String forRead(String dalName) {
        return "Not authorized to read entities in DAL [%s]".formatted(dalName);
    }

    @Override
    public String forReadOne(String dalName, Object id) {
        return "Not authorized to read entity with ID [%s] in DAL [%s]".formatted(id, dalName);
    }

    @Override
    public String forUpdate(String dalName, Object id) {
        return "Not authorized to update entity with ID [%s] in DAL [%s]".formatted(id, dalName);
    }

    @Override
    public String forDelete(String dalName, Object id) {
        return "Not authorized to delete entity with ID [%s] in DAL [%s]".formatted(id, dalName);
    }
}
