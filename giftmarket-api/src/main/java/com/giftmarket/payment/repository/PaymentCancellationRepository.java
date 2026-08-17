package com.giftmarket.payment.repository;

import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentCancellationRepository extends JpaRepository<PaymentCancellation, Long> {
    Optional<PaymentCancellation> findByClientRequestKeyAndPaymentOrderUserId(String clientRequestKey, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PaymentCancellation c where c.id = :id")
    Optional<PaymentCancellation> findByIdForUpdate(@Param("id") Long id);

    Optional<PaymentCancellation> findFirstByPaymentIdAndStatusOrderByIdDesc(Long paymentId, PaymentCancellationStatus status);
}
