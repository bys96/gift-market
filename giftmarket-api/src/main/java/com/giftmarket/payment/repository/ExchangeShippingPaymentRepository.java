package com.giftmarket.payment.repository;

import com.giftmarket.payment.entity.ExchangeShippingPayment;
import com.giftmarket.payment.entity.ExchangeShippingPaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExchangeShippingPaymentRepository extends JpaRepository<ExchangeShippingPayment, Long> {
    Optional<ExchangeShippingPayment> findByExchangeRequestId(Long exchangeRequestId);
    Optional<ExchangeShippingPayment> findByProviderOrderId(String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExchangeShippingPayment p where p.id = :id")
    Optional<ExchangeShippingPayment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExchangeShippingPayment p where p.exchangeRequest.id = :requestId")
    Optional<ExchangeShippingPayment> findByExchangeRequestIdForUpdate(@Param("requestId") Long requestId);

    @Query("select p.id from ExchangeShippingPayment p where p.status = :status and p.requestedAt <= :before order by p.id")
    List<Long> findReconciliationCandidateIds(@Param("status") ExchangeShippingPaymentStatus status,
                                               @Param("before") LocalDateTime before, Pageable pageable);
}
