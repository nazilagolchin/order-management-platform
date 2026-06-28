package com.nazila.ordermgmt.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazila.ordermgmt.events.EventType;
import com.nazila.ordermgmt.events.OrderCreatedEvent;
import com.nazila.ordermgmt.events.OrderLineItem;
import com.nazila.ordermgmt.inventory.domain.Inventory;
import com.nazila.ordermgmt.inventory.domain.ReservationStatus;
import com.nazila.ordermgmt.inventory.domain.StockReservation;
import com.nazila.ordermgmt.inventory.outbox.InventoryOutboxEvent;
import com.nazila.ordermgmt.inventory.outbox.InventoryOutboxEventRepository;
import com.nazila.ordermgmt.inventory.repository.InventoryRepository;
import com.nazila.ordermgmt.inventory.repository.StockReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private StockReservationRepository reservationRepository;

    @Mock
    private InventoryOutboxEventRepository outboxEventRepository;

    private InventoryServiceImpl inventoryService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private OrderCreatedEvent eventFor(UUID orderId, UUID productId, int quantity) {
        return new OrderCreatedEvent(orderId, UUID.randomUUID(), "USD", BigDecimal.TEN,
                List.of(new OrderLineItem(productId, quantity, new BigDecimal("10.00"))));
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        inventoryService = new InventoryServiceImpl(
                inventoryRepository, reservationRepository, outboxEventRepository, objectMapper);
    }

    @Test
    void reservesStockAndPublishesReservedEventWhenAvailable() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(inventoryRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(new Inventory(productId, 10)));

        inventoryService.reserveStock(eventFor(orderId, productId, 4), "corr-1");

        ArgumentCaptor<StockReservation> reservationCaptor = ArgumentCaptor.forClass(StockReservation.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(ReservationStatus.RESERVED);

        ArgumentCaptor<InventoryOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(InventoryOutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(EventType.INVENTORY_RESERVED);
        assertThat(outboxCaptor.getValue().getAggregateId()).isEqualTo(orderId);
    }

    @Test
    void recordsFailureAndPublishesFailedEventWhenStockIsInsufficient() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(inventoryRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(new Inventory(productId, 1)));

        inventoryService.reserveStock(eventFor(orderId, productId, 4), "corr-2");

        ArgumentCaptor<StockReservation> reservationCaptor = ArgumentCaptor.forClass(StockReservation.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(ReservationStatus.FAILED);

        ArgumentCaptor<InventoryOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(InventoryOutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(EventType.INVENTORY_RESERVATION_FAILED);
    }

    @Test
    void unknownProductIsTreatedAsInsufficientStock() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(inventoryRepository.findByIdForUpdate(productId)).thenReturn(Optional.empty());

        inventoryService.reserveStock(eventFor(orderId, productId, 1), "corr-3");

        ArgumentCaptor<StockReservation> reservationCaptor = ArgumentCaptor.forClass(StockReservation.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(ReservationStatus.FAILED);
    }

    @Test
    void redeliveredEventForAnAlreadyDecidedOrderIsSkipped() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(StockReservation.reserved(orderId)));

        inventoryService.reserveStock(eventFor(orderId, productId, 1), "corr-4");

        verify(inventoryRepository, never()).findByIdForUpdate(any());
        verify(reservationRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void getAvailableQuantityReturnsCurrentStock() {
        UUID productId = UUID.randomUUID();
        when(inventoryRepository.findById(productId)).thenReturn(Optional.of(new Inventory(productId, 7)));

        assertThat(inventoryService.getAvailableQuantity(productId)).isEqualTo(7);
    }
}
