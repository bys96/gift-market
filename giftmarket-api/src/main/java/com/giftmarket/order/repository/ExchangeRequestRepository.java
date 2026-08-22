package com.giftmarket.order.repository;

import com.giftmarket.order.entity.ExchangeRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExchangeRequestRepository extends JpaRepository<ExchangeRequest, Long> {
    Optional<ExchangeRequest> findByClientRequestKey(String clientRequestKey);

    List<ExchangeRequest> findAllByOrderIdOrderByRequestedAtDescIdDesc(Long orderId);

    List<ExchangeRequest> findAllBySellerOrderIdOrderByRequestedAtDescIdDesc(Long sellerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExchangeRequest e where e.id = :id")
    Optional<ExchangeRequest> findByIdForUpdate(@Param("id") Long id);
}
