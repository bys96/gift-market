package com.giftmarket.payment.repository;

import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.payment.entity.PaymentStatus;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository
        extends JpaRepository<Payment, Long> {

    List<Payment> findAllByOrderIdOrderByIdAsc(
            Long orderId
    );

    Optional<Payment> findFirstByOrderIdOrderByIdDesc(Long orderId);
    Optional<Payment> findFirstByOrderIdAndOrderUserIdOrderByIdDesc(Long orderId, Long userId);

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

    @Query("""
            select p.id
            from Payment p
            where p.status = :paymentStatus
              and p.expiresAt <= :now
              and p.order.status = :orderStatus
            order by p.expiresAt asc, p.id asc
            """)
    List<Long> findExpirationCandidateIds(
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Payment p
            where p.id = :paymentId
            """)
    Optional<Payment> findByIdForUpdate(
            @Param("paymentId") Long paymentId
    );

    @Query("""
            select p.id
            from Payment p
            where p.status = :paymentStatus
              and p.confirmingAt <= :confirmingBefore
              and p.order.status = :orderStatus
            order by p.confirmingAt asc, p.id asc
            """)
    List<Long> findReconciliationCandidateIds(
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("confirmingBefore") LocalDateTime confirmingBefore,
            Pageable pageable
    );
}
