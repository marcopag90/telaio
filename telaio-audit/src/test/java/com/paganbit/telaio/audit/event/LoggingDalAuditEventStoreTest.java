package com.paganbit.telaio.audit.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.paganbit.telaio.audit.event.format.DalAuditEventFormatter;
import com.paganbit.telaio.core.adapter.DalOperationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LoggingDalAuditEventStoreTest {

    private static final String CATEGORY = "test.telaio.audit.category";

    private final DalAuditEventFormatter formatter = event -> "FORMATTED";
    private final LoggingDalAuditEventStore store = new LoggingDalAuditEventStore(formatter, CATEGORY);

    private Logger categoryLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        categoryLogger = (Logger) LoggerFactory.getLogger(CATEGORY);
        appender = new ListAppender<>();
        appender.start();
        categoryLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        categoryLogger.detachAppender(appender);
    }

    @Test
    void successfulEvent_shouldLogAtInfoUnderTheConfiguredCategory() {
        store.store(event(DalAuditOutcome.SUCCESS, null, null));

        assertThat(appender.list).singleElement().satisfies(logged -> {
            assertThat(logged.getLevel()).isEqualTo(Level.INFO);
            assertThat(logged.getLoggerName()).isEqualTo(CATEGORY);
            assertThat(logged.getFormattedMessage()).isEqualTo("FORMATTED");
        });
    }

    @Test
    void deniedEvent_shouldLogAtWarn() {
        store.store(event(DalAuditOutcome.DENIED, "java.lang.SecurityException", "forbidden"));

        assertThat(appender.list).singleElement()
            .extracting(ILoggingEvent::getLevel).isEqualTo(Level.WARN);
    }

    @Test
    void errorEvent_shouldLogAtWarn() {
        store.store(event(DalAuditOutcome.ERROR, "java.lang.RuntimeException", "boom"));

        assertThat(appender.list).singleElement()
            .extracting(ILoggingEvent::getLevel).isEqualTo(Level.WARN);
    }

    @Test
    void store_shouldNotThrow() {
        assertThatCode(() -> store.store(event(DalAuditOutcome.SUCCESS, null, null)))
            .doesNotThrowAnyException();
    }

    @Test
    void throwingFormatter_shouldNotPropagate_andLogFallbackAtWarn() {
        LoggingDalAuditEventStore throwingStore = new LoggingDalAuditEventStore(
            e -> {
                throw new RuntimeException("boom");
            }, CATEGORY);

        assertThatCode(() -> throwingStore.store(event(DalAuditOutcome.SUCCESS, null, null)))
            .doesNotThrowAnyException();

        assertThat(appender.list).singleElement().satisfies(logged -> {
            assertThat(logged.getLevel()).isEqualTo(Level.WARN);
            assertThat(logged.getFormattedMessage())
                .contains("dal=products", "outcome=SUCCESS", "audit formatting failed");
        });
    }

    private static DalAuditEvent event(
        DalAuditOutcome outcome, String errorType, String errorMessage) {
        return new DalAuditEvent(
            Instant.parse("2026-06-12T10:00:00Z"), "products", DalOperationType.CREATE, "admin",
            Map.of("input", Map.of("name", "Widget")), outcome, errorType, errorMessage,
            Duration.ofMillis(12));
    }
}
