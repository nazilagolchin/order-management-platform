package com.nazila.ordermgmt.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/**
 * Aggregate root for the inventory bounded context: one row per product,
 * tracking how many units are available to reserve. Reservation locks the
 * row ({@code SELECT ... FOR UPDATE} via {@code InventoryRepository}) rather
 * than relying on optimistic retry, because a hot product under concurrent
 * orders would otherwise churn through repeated {@code OptimisticLockException}
 * retries; {@code @Version} is kept anyway as a defense-in-depth check against
 * any write that bypasses the locking read.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Version
    @Column(nullable = false)
    private long version;

    protected Inventory() {
        // JPA
    }

    public Inventory(UUID productId, int availableQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
    }

    public boolean hasSufficientStock(int quantity) {
        return availableQuantity >= quantity;
    }

    public void reserve(int quantity) {
        if (!hasSufficientStock(quantity)) {
            throw new IllegalStateException(
                    "Cannot reserve " + quantity + " of product " + productId + "; only " + availableQuantity + " available.");
        }
        this.availableQuantity -= quantity;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public long getVersion() {
        return version;
    }
}
