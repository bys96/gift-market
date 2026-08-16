package com.giftmarket.payment.repository;

import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository
        extends JpaRepository<Payment, Long> {

    List<Payment> findAllByOrderIdOrderByIdAsc(
            Long orderId
    );

    Optional<Payment> findByMerchantPaymentId(
            String merchantPaymentId
    );

    Optional<Payment> findByClientRequestKey(
            String clientRequestKey
    );

    Optional<Payment> findByClientRequestKeyAndOrderUserId(
            String clientRequestKey,
            Long userId
    );

    Optional<Payment> findByProviderAndProviderPaymentKey(
            PaymentProvider provider,
            String providerPaymentKey
    );

    Optional<Payment> findByIdAndOrderUserId(
            Long paymentId,
            Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Payment p
            where p.id = :paymentId
              and p.order.user.id = :userId
            """)
    Optional<Payment> findByIdAndOrderUserIdForUpdate(
            @Param("paymentId") Long paymentId,
            @Param("userId") Long userId
    );
}
