package com.giftmarket.payment.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.order.entity.OrderCancellation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "payment_cancellations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_cancellations_client_key", columnNames = "client_request_key"),
        @UniqueConstraint(name = "uk_payment_cancellations_idempotency_key", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_payment_cancellations_order_cancellation", columnNames = "order_cancellation_id")
}, indexes = {
        @Index(name = "idx_payment_cancellations_payment_status", columnList = "payment_id, status"),
        @Index(name = "idx_payment_cancellations_status_requested_at", columnList = "status, requested_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancellation extends BaseEntity {
    private static final int MAX_PROVIDER_REASON_LENGTH = 200;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_cancellation_id")
    private OrderCancellation orderCancellation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'FULL'")
    private PaymentCancellationType type;

    @Column(name = "client_request_key", nullable = false, length = 100)
    private String clientRequestKey;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentCancellationStatus status;

    @Column(name = "provider_transaction_key", length = 200)
    private String providerTransactionKey;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    public static PaymentCancellation create(Payment payment, String clientRequestKey,
                                               String idempotencyKey, String reason, LocalDateTime now) {
        PaymentCancellation value = new PaymentCancellation();
        value.payment = payment;
        value.type = PaymentCancellationType.FULL;
        value.clientRequestKey = clientRequestKey;
        value.idempotencyKey = idempotencyKey;
        value.amount = payment.getAmount();
        value.reason = requireProviderReason(reason);
        value.status = PaymentCancellationStatus.REQUESTED;
        value.requestedAt = now;
        return value;
    }

    public static PaymentCancellation createPartial(
            Payment payment,
            OrderCancellation orderCancellation,
            String clientRequestKey,
            String idempotencyKey,
            Long amount,
            String reason,
            LocalDateTime now
    ) {
        if (payment == null || orderCancellation == null
                || orderCancellation.getOrder() != payment.getOrder()) {
            throw new IllegalArgumentException("부분환불 주문 정보가 결제와 일치하지 않습니다.");
        }
        if (amount == null || amount <= 0L || amount > payment.getAmount()) {
            throw new IllegalArgumentException("부분환불 금액이 올바르지 않습니다.");
        }
        PaymentCancellation value = new PaymentCancellation();
        value.payment = payment;
        value.orderCancellation = orderCancellation;
        value.type = PaymentCancellationType.PARTIAL;
        value.clientRequestKey = requireText(clientRequestKey, "부분환불 요청 키가 필요합니다.");
        value.idempotencyKey = requireText(idempotencyKey, "PG 멱등성 키가 필요합니다.");
        value.amount = amount;
        value.reason = requireProviderReason(reason);
        value.status = PaymentCancellationStatus.REQUESTED;
        value.requestedAt = now;
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String requireProviderReason(String reason) {
        String normalized = requireText(reason, "PG 취소 사유가 필요합니다.");
        if (normalized.length() > MAX_PROVIDER_REASON_LENGTH) {
            throw new IllegalArgumentException("PG 취소 사유는 200자 이내여야 합니다.");
        }
        return normalized;
    }

    public void succeed(String providerTransactionKey, LocalDateTime canceledAt) {
        this.status = PaymentCancellationStatus.SUCCEEDED;
        this.providerTransactionKey = providerTransactionKey;
        this.canceledAt = canceledAt;
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void fail(String code, String message, LocalDateTime now) {
        this.status = PaymentCancellationStatus.FAILED;
        this.failureCode = code;
        this.failureMessage = message;
        this.failedAt = now;
    }
}
