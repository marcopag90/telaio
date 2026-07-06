package com.paganbit.telaio.showcase.it;

import com.paganbit.telaio.audit.event.DalAuditEvent;
import com.paganbit.telaio.audit.event.DalAuditEventStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Replaces the default {@code LoggingDalAuditEventStore} with an in-memory capturing store so the
 * integration tests can assert on {@link DalAuditEvent}s deterministically (audit is otherwise only
 * observable through SLF4J). The default store is {@code @ConditionalOnMissingBean}, so declaring this
 * bean is enough to take over.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuditCaptureTestConfig {

    @Bean
    CapturingDalAuditEventStore capturingDalAuditEventStore() {
        return new CapturingDalAuditEventStore();
    }

    /**
     * Thread-safe, replayable audit sink. {@link #clear()} resets it before a scenario so assertions
     * see only the events that scenario produced.
     */
    public static class CapturingDalAuditEventStore implements DalAuditEventStore {

        private final List<DalAuditEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void store(DalAuditEvent event) {
            events.add(event);
        }

        public List<DalAuditEvent> events() {
            return List.copyOf(events);
        }

        public void clear() {
            events.clear();
        }
    }
}
