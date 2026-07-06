package com.paganbit.telaio.core.version;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;

/**
 * Resolves the Telaio library's own version, independently of any consuming application.
 *
 * <p>The version is read from a build-filtered, library-scoped classpath resource
 * ({@value #VERSION_RESOURCE}) that carries telaio-core's {@code @project.version@} placeholder.
 * Because every Telaio module shares the same {@code ${project.version}}, this is the version of the
 * framework as a whole. It is deliberately <em>not</em> taken from the application-global
 * {@code GitProperties} bean, which would report the consuming application's version.</p>
 *
 * <p>Living in telaio-core lets any module expose the library version without re-implementing the
 * resource lookup and without depending on telaio-web.</p>
 *
 * <p>The version is immutable for the JVM's lifetime, so it is resolved once when this provider is
 * constructed and cached; {@link #getVersion()} simply returns the cached value.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class TelaioVersionProvider {

    static final String VERSION_RESOURCE = "com/paganbit/telaio/core/telaio-version.properties";
    static final String VERSION_KEY = "telaio.version";
    static final String UNKNOWN_VERSION = "unknown";

    private final String version;

    public TelaioVersionProvider() {
        this.version = resolveVersion();
    }

    /**
     * Returns the Telaio library version, resolved once at construction time and cached for this
     * provider's lifetime.
     *
     * @return the resolved library version, or {@value #UNKNOWN_VERSION} when it cannot be determined
     */
    public String getVersion() {
        return version;
    }

    /**
     * Resolves the Telaio library version from its build-filtered classpath resource. Falls back to
     * {@value #UNKNOWN_VERSION} when the resource is missing or, in an unfiltered build (e.g., an IDE
     * that skipped resource filtering), still holds the literal {@code @project.version@}.
     */
    private static String resolveVersion() {
        try {
            final var props = PropertiesLoaderUtils.loadProperties(new ClassPathResource(VERSION_RESOURCE));
            final var version = props.getProperty(VERSION_KEY);
            return version != null && !version.isBlank() && !version.startsWith("@")
                ? version
                : UNKNOWN_VERSION;
        } catch (IOException unavailable) {
            return UNKNOWN_VERSION;
        }
    }
}
