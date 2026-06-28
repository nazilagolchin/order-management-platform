package com.nazila.ordermgmt.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per order this service has already processed a reservation
 * decision for. Its sole purpose is consumer idempotency: {@code
 * OrderCreatedEvent} delivery is at least once (see {@code
 * docs/outbox-pattern.md}), so before reserving stock the listener checks
 * for an existing row by {@code orderId} and skips reprocessing if one
 * exists — otherwise a redelivered event would decrement stock twice for
 * the same order.
 */
@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockReservation() {
        // JPA
    }

    private StockReservation(UUID id, UUID orderId, ReservationStatus status, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static StockReservation reserved(UUID orderId) {
        return new StockReservation(UUID.randomUUID(), orderId, ReservationStatus.RESERVED, Instant.now());
    }

    public static StockReservation failed(UUID orderId) {
        return new StockReservation(UUID.randomUUID(), orderId, ReservationStatus.FAILED, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
