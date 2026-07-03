package io.paganbit.telaio.audit.autoconfigure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Telaio Audit, bound to the {@code telaio.audit} prefix.
 *
 * <p>The {@code @Validated} constraints below are enforced only when a Bean Validation provider (e.g.
 * Hibernate Validator) is on the classpath; {@code jakarta.validation-api} is an optional dependency,
 * so without a provider the constraints are silently skipped and the consumer decides whether to add
 * one.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@ConfigurationProperties("telaio.audit")
@Validated
public class TelaioAuditProperties {

    @Valid
    private final Logging logging = new Logging();

    public Logging getLogging() {
        return logging;
    }

    /**
     * Settings for the default {@code LoggingDalAuditEventStore}, which writes each audit event to
     * the application log.
     */
    public static class Logging {

        /**
         * How each audit event is serialized to the log. {@code TEXT} emits a human-readable
         * {@code key=value} (logfmt) line for development; {@code JSON} emits one JSON object per
         * line (JSON Lines) for ingestion by log aggregators.
         */
        @NotNull
        private Format format = Format.TEXT;

        /**
         * Logger category under which audit events are emitted. A dedicated, stable category lets the
         * host application route auditing to its own appender (a separate file, index, or sourcetype)
         * with a {@code <logger>} element, keeping the audit trail isolated from other application
         * logs regardless of how they are configured.
         */
        @NotBlank
        private String category = "io.paganbit.telaio.audit.AUDIT";

        /**
         * Whether the current {@code MDC} (Mapped Diagnostic Context) is copied onto each audit
         * event. When enabled, correlation keys populated upstream — for example {@code traceId} and
         * {@code spanId} from Micrometer Tracing — are emitted with the event so it can be tied back
         * to the originating request in the aggregator.
         */
        private boolean includeMdc = true;

        public Format getFormat() {
            return format;
        }

        public void setFormat(Format format) {
            this.format = format;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public boolean isIncludeMdc() {
            return includeMdc;
        }

        public void setIncludeMdc(boolean includeMdc) {
            this.includeMdc = includeMdc;
        }
    }

    /**
     * Serialization format of the logging audit store.
     */
    public enum Format {

        /**
         * Human-readable {@code key=value} (logfmt) single line.
         */
        TEXT,

        /**
         * One JSON object per line (JSON Lines), parsed natively by most log aggregators.
         */
        JSON
    }
}
