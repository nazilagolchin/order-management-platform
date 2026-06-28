package com.nazila.ordermgmt.shared.correlation;

import org.slf4j.MDC;

/**
 * Reads the correlation id that {@link CorrelationIdFilter} placed into MDC
 * for the current thread. Services use this to stamp the id onto outgoing
 * Kafka event envelopes so a single business transaction can be traced across
 * HTTP, the outbox, and every downstream consumer.
 */
public final class CorrelationContext {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationContext() {
    }

    /**
     * Returns the current correlation id, or {@code null} if called outside
     * a request thread that went through {@link CorrelationIdFilter} (e.g. a
     * Kafka consumer thread, which must set its own correlation id explicitly
     * from the inbound event envelope).
     */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static void set(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
