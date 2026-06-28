package com.nazila.ordermgmt.inventory.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTest {

    @Test
    void reserveDecrementsAvailableQuantity() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 10);

        inventory.reserve(4);

        assertThat(inventory.getAvailableQuantity()).isEqualTo(6);
    }

    @Test
    void reservingMoreThanAvailableThrowsAndLeavesQuantityUnchanged() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 3);

        assertThatThrownBy(() -> inventory.reserve(4)).isInstanceOf(IllegalStateException.class);

        assertThat(inventory.getAvailableQuantity()).isEqualTo(3);
    }

    @Test
    void hasSufficientStockIsExactAtTheBoundary() {
        Inventory inventory = new Inventory(UUID.randomUUID(), 5);

        assertThat(inventory.hasSufficientStock(5)).isTrue();
        assertThat(inventory.hasSufficientStock(6)).isFalse();
    }
}
