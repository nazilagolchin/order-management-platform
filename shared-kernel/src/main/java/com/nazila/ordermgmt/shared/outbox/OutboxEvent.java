package com.nazila.ordermgmt.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;
import java.util.UUID;

/**
 * Base columns for a service's {@code outbox_events} table. Each service
 * declares its own thin {@code @Entity} subclass (mapped to its own schema,
 * in its own database) so the outbox stays owned by the service that writes
 * it, while the relay polling/publish logic lives once in
 * {@link OutboxRelay}. See {@code docs/outbox-pattern.md} for why this table
 * exists: it's what makes "commit the business write" and "publish the
 * event" atomic without a distributed transaction.
 */
@MappedSuperclass
public abstract class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
        // JPA
    }

    protected OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType,
                           String payload, int eventVersion, String correlationId, Instant createdAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.eventVersion = eventVersion;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
