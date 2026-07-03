package io.paganbit.telaio.core.version;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link TelaioVersionProvider} resolves the Telaio library's own version from its
 * build-filtered classpath resource, never the unfiltered placeholder or the {@code unknown}
 * fallback.
 */
class TelaioVersionProviderTest {

    private final TelaioVersionProvider versionProvider = new TelaioVersionProvider();

    @Test
    void getVersion_shouldReturnTheBuildFilteredVersion() {
        // mvn processes (filters) the resource into the test classpath, so the placeholder is gone.
        final var version = versionProvider.getVersion();
        assertThat(version)
            .isNotBlank()
            .isNotEqualTo(TelaioVersionProvider.UNKNOWN_VERSION)
            .doesNotStartWith("@");
    }
}
