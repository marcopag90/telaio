package com.paganbit.telaio.security;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Utility class for reading thread-bound Spring Security and web request context state.
 * <p>
 * This helper exposes null-safe accessors to the current {@link Authentication} and
 * {@link RequestAttributes} associated with the executing thread.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public final class DalSecurityContextHelper {

    private DalSecurityContextHelper() {

    }

    /**
     * Returns the current Spring Security {@link Authentication} from the active
     * {@link org.springframework.security.core.context.SecurityContext}.
     *
     * @return the current authentication, or {@code null} when no authentication is present
     * for the current thread
     */
    public static @Nullable Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Returns the current {@link RequestAttributes} bound to the executing thread.
     *
     * @return the current request attributes, or {@code null} when no web request context
     * is bound (for example, in non-web/background execution)
     */
    public static @Nullable RequestAttributes getCurrentRequestAttributes() {
        return RequestContextHolder.getRequestAttributes();
    }
}
