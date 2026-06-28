package com.nazila.ordermgmt.events;

import java.time.Instant;
import java.util.UUID;

/**
 * The wire format for every event published to Kafka. {@code payload} carries
 * the event-specific fields (e.g. {@link OrderCreatedEvent}); consumers
 * deserialize it generically (as a {@code Map}) and then convert it to the
 * concrete type once they've checked {@code eventType}, since Kafka headers
 * carry no Java type information across services.
 *
 * <p>{@code eventVersion} lets a future schema change be detected by a
 * consumer instead of silently misread: a consumer that receives a version it
 * doesn't recognize should route the message to its dead-letter topic rather
 * than guess at the shape.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        UUID aggregateId,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        Object payload) {

    public static final int CURRENT_VERSION = 1;

    public static EventEnvelope of(String eventType, UUID aggregateId, String correlationId, Object payload) {
        return new EventEnvelope(UUID.randomUUID(), eventType, aggregateId, CURRENT_VERSION, Instant.now(), correlationId, payload);
    }
}
