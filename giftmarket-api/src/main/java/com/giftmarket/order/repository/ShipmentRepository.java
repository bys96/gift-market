package com.giftmarket.order.repository;

import com.giftmarket.order.entity.Shipment;
import com.giftmarket.order.entity.ShipmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findBySellerOrderIdAndType(
            Long sellerOrderId,
            ShipmentType type
    );

    List<Shipment> findAllBySellerOrderIdInAndType(
            List<Long> sellerOrderIds,
            ShipmentType type
    );

    boolean existsBySellerOrderIdAndType(
            Long sellerOrderId,
            ShipmentType type
    );
}
