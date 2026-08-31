package com.giftmarket.order.repository;

import com.giftmarket.order.entity.Shipment;
import com.giftmarket.order.entity.ShipmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

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

    List<Shipment> findAllBySellerOrderIdInOrderBySellerOrderIdAscIdAsc(List<Long> sellerOrderIds);

    boolean existsBySellerOrderIdAndType(
            Long sellerOrderId,
            ShipmentType type
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shipment s where s.id = :id")
    Optional<Shipment> findByIdForUpdate(@Param("id") Long id);
}
