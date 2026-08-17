package com.giftmarket.order.repository;

import com.giftmarket.order.entity.OrderCancellation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderCancellationRepository extends JpaRepository<OrderCancellation, Long> {

    Optional<OrderCancellation> findByClientRequestKey(String clientRequestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OrderCancellation c where c.id = :id")
    Optional<OrderCancellation> findByIdForUpdate(@Param("id") Long id);
}
