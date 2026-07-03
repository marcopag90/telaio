package io.paganbit.telaio.security.adapter;

/**
 * RBAC adapter that performs no filtering, returning payloads unchanged.
 *
 * <p>This is the default RBAC adapter applied when a DAL declares no {@code rbacAdapterClass} in
 * {@code @DalSecurity}: it inherits the pass-through default methods of {@link DalRbacAdapter}.</p>
 *
 * @param <T> the exposed entity type
 * @author Marco Pagan
 * @since 1.0.0
 */
public class NoopDalRbacAdapter<T> implements DalRbacAdapter<T> {
}
