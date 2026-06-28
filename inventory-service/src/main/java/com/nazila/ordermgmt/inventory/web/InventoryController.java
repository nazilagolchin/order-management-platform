package com.nazila.ordermgmt.inventory.web;

import com.nazila.ordermgmt.inventory.service.InventoryService;
import com.nazila.ordermgmt.inventory.web.dto.InventoryResponse;
import com.nazila.ordermgmt.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get available stock for a product",
            description = "Read-only view of current stock; reservations are driven by OrderCreatedEvent, not this API.")
    @ApiResponse(responseCode = "200", description = "Product found")
    @ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    public InventoryResponse getInventory(@PathVariable UUID productId) {
        return new InventoryResponse(productId, inventoryService.getAvailableQuantity(productId));
    }
}
