package com.nazila.ordermgmt.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazila.ordermgmt.events.EventEnvelope;
import com.nazila.ordermgmt.events.EventType;
import com.nazila.ordermgmt.events.InventoryReservationFailedEvent;
import com.nazila.ordermgmt.events.InventoryReservedEvent;
import com.nazila.ordermgmt.events.OrderCreatedEvent;
import com.nazila.ordermgmt.inventory.domain.Inventory;
import com.nazila.ordermgmt.inventory.domain.StockReservation;
import com.nazila.ordermgmt.inventory.outbox.InventoryOutboxEvent;
import com.nazila.ordermgmt.inventory.outbox.InventoryOutboxEventRepository;
import com.nazila.ordermgmt.inventory.repository.InventoryRepository;
import com.nazila.ordermgmt.inventory.repository.StockReservationRepository;
import com.nazila.ordermgmt.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * There's nothing to compensate on a failed reservation (nothing was
 * reserved), so a failure is just one outbox event, not a rollback path —
 * see {@code docs/saga-flow.md}.
 */
@Service
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryRepository inventoryRepository;
    private final StockReservationRepository reservationRepository;
    private final InventoryOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public InventoryServiceImpl(InventoryRepository inventoryRepository,
                                 StockReservationRepository reservationRepository,
                                 InventoryOutboxEventRepository outboxEventRepository,
                                 ObjectMapper objectMapper) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void reserveStock(OrderCreatedEvent event, String correlationId) {
        if (reservationRepository.findByOrderId(event.orderId()).isPresent()) {
            log.info("Order {} already has a reservation decision recorded; skipping redelivered event", event.orderId());
            return;
        }

        Map<UUID, Integer> requestedQuantities = new LinkedHashMap<>();
        event.items().forEach(item -> requestedQuantities.merge(item.productId(), item.quantity(), Integer::sum));

        Map<UUID, Inventory> lockedRows = new LinkedHashMap<>();
        String failureReason = null;
        for (Map.Entry<UUID, Integer> requested : requestedQuantities.entrySet()) {
            Optional<Inventory> inventory = inventoryRepository.findByIdForUpdate(requested.getKey());
            if (inventory.isEmpty() || !inventory.get().hasSufficientStock(requested.getValue())) {
                failureReason = "Insufficient stock for product " + requested.getKey();
                break;
            }
            lockedRows.put(requested.getKey(), inventory.get());
        }

        if (failureReason != null) {
            recordFailure(event.orderId(), failureReason, correlationId);
            return;
        }

        requestedQuantities.forEach((productId, quantity) -> lockedRows.get(productId).reserve(quantity));
        recordSuccess(event.orderId(), correlationId);
    }

    @Override
    @Transactional(readOnly = true)
    public int getAvailableQuantity(UUID productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " was not found."))
                .getAvailableQuantity();
    }

    private void recordSuccess(UUID orderId, String correlationId) {
        reservationRepository.save(StockReservation.reserved(orderId));
        outboxEventRepository.save(buildOutboxEvent(
                orderId, EventType.INVENTORY_RESERVED, new InventoryReservedEvent(orderId), correlationId));
        log.info("Reserved stock for order {}", orderId);
    }

    private void recordFailure(UUID orderId, String reason, String correlationId) {
        reservationRepository.save(StockReservation.failed(orderId));
        outboxEventRepository.save(buildOutboxEvent(
                orderId, EventType.INVENTORY_RESERVATION_FAILED, new InventoryReservationFailedEvent(orderId, reason), correlationId));
        log.info("Reservation failed for order {}: {}", orderId, reason);
    }

    private InventoryOutboxEvent buildOutboxEvent(UUID orderId, String eventType, Object payload, String correlationId) {
        EventEnvelope envelope = EventEnvelope.of(eventType, orderId, correlationId, payload);
        return InventoryOutboxEvent.of(envelope.eventId(), orderId, envelope.eventType(),
                writeJson(envelope), envelope.eventVersion(), envelope.correlationId());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
