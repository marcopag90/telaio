package com.paganbit.telaio.metrics.store.jdbc;

import com.paganbit.telaio.metrics.autoconfigure.TelaioMetricsProperties;
import com.paganbit.telaio.metrics.autoconfigure.TelaioMetricsProperties.Jdbc.SchemaInitialization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
