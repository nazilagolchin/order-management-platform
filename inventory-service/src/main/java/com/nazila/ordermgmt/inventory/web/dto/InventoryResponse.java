package com.nazila.ordermgmt.inventory.web.dto;

import java.util.UUID;

public record InventoryResponse(UUID productId, int availableQuantity) {
}
