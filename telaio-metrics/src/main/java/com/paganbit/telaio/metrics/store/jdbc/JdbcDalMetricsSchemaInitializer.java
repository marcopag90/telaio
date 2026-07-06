package com.paganbit.telaio.metrics.store.jdbc;

import com.paganbit.telaio.metrics.autoconfigure.TelaioMetricsProperties;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.jdbc.init.PlatformPlaceholderDatabaseDriverResolver;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the metrics table from a per-vendor DDL script.
 *
 * <p>Mirrors the mechanism Spring Session, Batch, and Quartz use for their JDBC stores: the
 * {@code @@platform@@} placeholder in the script location is resolved against the
 * {@link DataSource} (or the configured platform override) by
 * {@link PlatformPlaceholderDatabaseDriverResolver}, and the resolved script is run by
 * {@link DataSourceScriptDatabaseInitializer}. Supported platforms ship a
 * {@code schema-<platform>.sql} on the classpath; production deployments that manage their schema
 * externally set {@code telaio.metrics.jdbc.initialize-schema=never} and apply those scripts with
 * their own migration tooling.</p>
 *
 * <p>The shipped scripts carry a {@code @@table_name@@} placeholder so the table (and its derived
 * constraint/index names) follow {@code telaio.metrics.jdbc.table-name}. Spring resolves only the
 * {@code @@platform@@} placeholder in the location, so the table-name substitution is applied to the
 * script <em>content</em> here, in {@code runScripts}, before the scripts run. Deployments that
 * apply the scripts externally ({@code initialize-schema=never}) must substitute
 * {@code @@table_name@@} themselves (default: {@code telaio_metrics_bucket}).</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class JdbcDalMetricsSchemaInitializer extends DataSourceScriptDatabaseInitializer {

    private static final String SCHEMA_LOCATION =
        "classpath:com/paganbit/telaio/metrics/store/jdbc/schema-@@platform@@.sql";

    /**
     * Placeholder in the shipped DDL scripts, replaced with the configured table name.
     */
    private static final String TABLE_NAME_PLACEHOLDER = "@@table_name@@";

    private final String tableName;

    public JdbcDalMetricsSchemaInitializer(
        DataSource dataSource,
        TelaioMetricsProperties.Jdbc jdbc
    ) {
        super(dataSource, settings(dataSource, jdbc));
        this.tableName = jdbc.getTableName();
    }

    /**
     * Substitutes the {@link #TABLE_NAME_PLACEHOLDER} in each resolved script with the configured
     * table name, then delegates to the standard JDBC runner. The table name is a validated SQL
     * identifier (see {@link JdbcDalMetricsStore#TABLE_NAME_PATTERN}), so the interpolation is safe.
     */
    @Override
    protected void runScripts(Scripts scripts) {
        Charset encoding = scripts.getEncoding() != null ? scripts.getEncoding() : StandardCharsets.UTF_8;
        List<Resource> substituted = new ArrayList<>();
        for (Resource script : scripts) {
            substituted.add(substitute(script, encoding));
        }
        Scripts resolved = new Scripts(substituted)
            .continueOnError(scripts.isContinueOnError())
            .separator(scripts.getSeparator());
        if (scripts.getEncoding() != null) {
            resolved.encoding(scripts.getEncoding());
        }
        super.runScripts(resolved);
    }

    private Resource substitute(Resource script, Charset encoding) {
        try {
            String content = script.getContentAsString(encoding).replace(TABLE_NAME_PLACEHOLDER, tableName);
            return new ByteArrayResource(content.getBytes(encoding), script.getDescription());
        } catch (IOException ex) {
            throw new UncheckedIOException(
                "Failed to read Telaio metrics schema script: " + script.getDescription(), ex);
        }
    }

    private static DatabaseInitializationSettings settings(
        DataSource dataSource,
        TelaioMetricsProperties.Jdbc jdbc
    ) {
        List<String> schemaLocations = resolveSchemaLocations(dataSource, jdbc.getPlatform());

        final var settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(schemaLocations);
        settings.setMode(toMode(jdbc.getInitializeSchema()));
        settings.setContinueOnError(false);
        boolean isOracle = schemaLocations.stream().anyMatch(loc -> loc.contains("schema-oracle.sql"));
        if (isOracle) {
            settings.setSeparator("/");
        }
        return settings;
    }

    private static List<String> resolveSchemaLocations(DataSource dataSource, String platform) {
        final var resolver = new PlatformPlaceholderDatabaseDriverResolver();
        if (StringUtils.hasText(platform)) {
            return resolver.resolveAll(platform, SCHEMA_LOCATION);
        }
        return resolver.resolveAll(dataSource, SCHEMA_LOCATION);
    }

    private static DatabaseInitializationMode toMode(
        TelaioMetricsProperties.Jdbc.SchemaInitialization initialization
    ) {
        return switch (initialization) {
            case ALWAYS -> DatabaseInitializationMode.ALWAYS;
            case EMBEDDED -> DatabaseInitializationMode.EMBEDDED;
            case NEVER -> DatabaseInitializationMode.NEVER;
        };
    }
}
