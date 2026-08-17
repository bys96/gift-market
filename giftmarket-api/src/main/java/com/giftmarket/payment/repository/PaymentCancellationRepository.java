package com.giftmarket.payment.repository;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.entity.PaymentCancellationType;
import com.giftmarket.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentCancellationRepository extends JpaRepository<PaymentCancellation, Long> {
    Optional<PaymentCancellation> findByClientRequestKeyAndPaymentOrderUserId(String clientRequestKey, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PaymentCancellation c where c.id = :id")
    Optional<PaymentCancellation> findByIdForUpdate(@Param("id") Long id);

    Optional<PaymentCancellation> findFirstByPaymentIdAndStatusOrderByIdDesc(Long paymentId, PaymentCancellationStatus status);

    Optional<PaymentCancellation> findByOrderCancellationId(Long orderCancellationId);

    @Query("""
            select coalesce(sum(c.amount), 0)
            from PaymentCancellation c
            where c.payment.id = :paymentId
              and c.status = :status
            """)
    Long sumAmountByPaymentIdAndStatus(
            @Param("paymentId") Long paymentId,
            @Param("status") PaymentCancellationStatus status
    );

    @Query("""
            select c.payment.id
            from PaymentCancellation c
            where c.status = :cancellationStatus
              and c.requestedAt <= :requestedBefore
              and c.payment.status = :paymentStatus
              and c.payment.order.status = :orderStatus
            order by c.requestedAt asc, c.id asc
            """)
    List<Long> findCancelReconciliationCandidatePaymentIds(
            @Param("cancellationStatus") PaymentCancellationStatus cancellationStatus,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("requestedBefore") LocalDateTime requestedBefore,
            Pageable pageable
    );

    @Query("""
            select c.id
            from PaymentCancellation c
            where c.type = :type
              and c.status = :cancellationStatus
              and c.requestedAt <= :requestedBefore
              and c.orderCancellation.status = com.giftmarket.order.entity.OrderCancellationStatus.PROCESSING
              and c.payment.status in :paymentStatuses
              and c.payment.order.status = :orderStatus
              and c.orderCancellation.sellerOrder.status in :sellerOrderStatuses
            order by c.requestedAt asc, c.id asc
            """)
    List<Long> findPartialReconciliationCandidateIds(
            @Param("type") PaymentCancellationType type,
            @Param("cancellationStatus") PaymentCancellationStatus cancellationStatus,
            @Param("paymentStatuses") List<PaymentStatus> paymentStatuses,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("sellerOrderStatuses") List<com.giftmarket.order.entity.SellerOrderStatus> sellerOrderStatuses,
            @Param("requestedBefore") LocalDateTime requestedBefore,
            Pageable pageable
    );

    @Query("""
            select c.id from PaymentCancellation c
            where c.payment.id = :paymentId
              and c.type = com.giftmarket.payment.entity.PaymentCancellationType.PARTIAL
              and c.status = com.giftmarket.payment.entity.PaymentCancellationStatus.REQUESTED
            order by c.requestedAt asc, c.id asc
            """)
    List<Long> findRequestedPartialIdsByPaymentId(@Param("paymentId") Long paymentId);
}
