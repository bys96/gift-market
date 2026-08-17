package com.giftmarket.payment.entity;

import com.giftmarket.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "payment_cancellations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_cancellations_client_key", columnNames = "client_request_key"),
        @UniqueConstraint(name = "uk_payment_cancellations_idempotency_key", columnNames = "idempotency_key")
}, indexes = @Index(name = "idx_payment_cancellations_payment_status", columnList = "payment_id, status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancellation extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

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
        value.clientRequestKey = clientRequestKey;
        value.idempotencyKey = idempotencyKey;
        value.amount = payment.getAmount();
        value.reason = reason;
        value.status = PaymentCancellationStatus.REQUESTED;
        value.requestedAt = now;
        return value;
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
