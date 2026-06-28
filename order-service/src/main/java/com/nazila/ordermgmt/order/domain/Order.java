package com.nazila.ordermgmt.order.domain;

import com.nazila.ordermgmt.shared.exception.BusinessRuleViolationException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for the order bounded context. All mutation goes through
 * methods here (never field setters from outside the package) so invariants
 * — e.g. the total always reflects the line items, a cancelled order can't
 * be cancelled again — hold no matter which layer calls in.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "idempotency_request_hash")
    private String idempotencyRequestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // JPA
    }

    private Order(UUID id, UUID customerId, String currency, Instant now) {
        this.id = id;
        this.customerId = customerId;
        this.currency = currency;
        this.status = OrderStatus.PENDING;
        this.totalAmount = BigDecimal.ZERO;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Order create(UUID customerId, String currency) {
        return new Order(UUID.randomUUID(), customerId, currency, Instant.now());
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.assignTo(this);
        recalculateTotal();
    }

    public void markIdempotent(String idempotencyKey, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.idempotencyRequestHash = requestHash;
    }

    public void cancel() {
        if (status == OrderStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Order " + id + " is already cancelled.");
        }
        this.status = OrderStatus.CANCELLED;
        touch();
    }

    public void confirm() {
        if (status != OrderStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Order " + id + " cannot be confirmed from status " + status + ".");
        }
        this.status = OrderStatus.CONFIRMED;
        touch();
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getIdempotencyRequestHash() {
        return idempotencyRequestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
