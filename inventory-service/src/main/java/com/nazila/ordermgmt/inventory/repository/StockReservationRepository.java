package com.nazila.ordermgmt.inventory.repository;

import com.nazila.ordermgmt.inventory.domain.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    Optional<StockReservation> findByOrderId(UUID orderId);
}
