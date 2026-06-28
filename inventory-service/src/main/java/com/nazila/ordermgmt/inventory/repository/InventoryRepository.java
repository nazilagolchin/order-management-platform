package com.nazila.ordermgmt.inventory.repository;

import com.nazila.ordermgmt.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    /**
     * Locks the row for the duration of the transaction so two concurrent
     * reservations against the same product can't both read the same
     * {@code availableQuantity} and oversell it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.productId = :productId")
    Optional<Inventory> findByIdForUpdate(UUID productId);
}
