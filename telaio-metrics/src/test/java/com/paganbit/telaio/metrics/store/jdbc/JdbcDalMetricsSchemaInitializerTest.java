package com.paganbit.telaio.metrics.store.jdbc;

import com.paganbit.telaio.metrics.autoconfigure.TelaioMetricsProperties;
import com.paganbit.telaio.metrics.autoconfigure.TelaioMetricsProperties.Jdbc.SchemaInitialization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.sql.init.AbstractScriptDatabaseInitializer.Scripts;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcDalMetricsSchemaInitializerTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        jdbcTemplate = new JdbcTemplate(database);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    private TelaioMetricsProperties.Jdbc jdbc(SchemaInitialization mode, String platform) {
        TelaioMetricsProperties properties = new TelaioMetricsProperties();
        properties.getJdbc().setInitializeSchema(mode);
        if (platform != null) {
            properties.getJdbc().setPlatform(platform);
        }
        return properties.getJdbc();
    }

    @Test
    void always_shouldCreateTable() {
        boolean initialized = new JdbcDalMetricsSchemaInitializer(
            database, jdbc(SchemaInitialization.ALWAYS, null)).initializeDatabase();

        assertThat(initialized).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM telaio_metrics_bucket", Integer.class)).isZero();
    }

    @Test
    void always_shouldBeIdempotent() {
        TelaioMetricsProperties.Jdbc jdbc = jdbc(SchemaInitialization.ALWAYS, null);
        new JdbcDalMetricsSchemaInitializer(database, jdbc).initializeDatabase();

        // Second run hits the IF NOT EXISTS guards in schema-h2.sql — must not throw
        assertThatCode(() -> new JdbcDalMetricsSchemaInitializer(database, jdbc).initializeDatabase())
            .doesNotThrowAnyException();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM telaio_metrics_bucket", Integer.class)).isZero();
    }

    @Test
    void always_withCustomTableName_shouldDriveTheDdl() {
        TelaioMetricsProperties.Jdbc jdbc = jdbc(SchemaInitialization.ALWAYS, null);
        jdbc.setTableName("custom_metrics");

        boolean initialized = new JdbcDalMetricsSchemaInitializer(database, jdbc).initializeDatabase();

        assertThat(initialized).isTrue();
        // The @@table_name@@ placeholder was substituted: the custom table (and its derived index
        // 'custom_metrics_ix1') exists and is queryable.
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM custom_metrics", Integer.class)).isZero();
        // The default-named table was not created.
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'TELAIO_METRICS_BUCKET'",
            Integer.class)).isZero();
    }

    @Test
    void never_shouldSkipInitialization() {
        boolean initialized = new JdbcDalMetricsSchemaInitializer(
            database, jdbc(SchemaInitialization.NEVER, null)).initializeDatabase();

        assertThat(initialized).isFalse();
        Integer tableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'TELAIO_METRICS_BUCKET'",
            Integer.class);
        assertThat(tableCount).isZero();
    }

    @Test
    void embedded_withEmbeddedDatabase_shouldCreateTable() {
        boolean initialized = new JdbcDalMetricsSchemaInitializer(
            database, jdbc(SchemaInitialization.EMBEDDED, null)).initializeDatabase();

        assertThat(initialized).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM telaio_metrics_bucket", Integer.class)).isZero();
    }

    @Test
    void runScripts_shouldHonorAnExplicitEncodingAndSubstituteTheTableName() {
        TelaioMetricsProperties.Jdbc jdbc = jdbc(SchemaInitialization.NEVER, null);
        jdbc.setTableName("encoded_metrics");
        JdbcDalMetricsSchemaInitializer initializer = new JdbcDalMetricsSchemaInitializer(database, jdbc);
        Resource ddl = new ByteArrayResource(
            "CREATE TABLE @@table_name@@ (id INT)".getBytes(StandardCharsets.UTF_8), "inline ddl");

        initializer.runScripts(new Scripts(List.of(ddl)).encoding(StandardCharsets.UTF_8));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM encoded_metrics", Integer.class)).isZero();
    }

    @Test
    void runScripts_withUnreadableScript_shouldFailWithTheScriptDescription() throws IOException {
        JdbcDalMetricsSchemaInitializer initializer =
            new JdbcDalMetricsSchemaInitializer(database, jdbc(SchemaInitialization.NEVER, null));
        Resource broken = mock(Resource.class);
        when(broken.getContentAsString(any())).thenThrow(new IOException("disk gone"));
        when(broken.getDescription()).thenReturn("broken script");
        Scripts scripts = new Scripts(List.of(broken));

        assertThatThrownBy(() -> initializer.runScripts(scripts))
            .isInstanceOf(UncheckedIOException.class)
            .hasMessageContaining("broken script")
            .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void oracle_neverMode_shouldNotThrow() {
        // Verifies that oracle platform selection (which sets separator='/') does not crash during
        // initializer construction or when mode=never skips script execution entirely.
        // End-to-end execution of the PL/SQL script is covered by JdbcDalMetricsStoreVendorTest.
        assertThatCode(() -> {
            boolean initialized = new JdbcDalMetricsSchemaInitializer(
                database, jdbc(SchemaInitialization.NEVER, "oracle")).initializeDatabase();
            assertThat(initialized).isFalse();
        }).doesNotThrowAnyException();
    }
}
