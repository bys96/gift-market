package com.giftmarket.payment.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payments_merchant_payment_id",
                        columnNames = "merchant_payment_id"
                ),
                @UniqueConstraint(
                        name = "uk_payments_client_request_key",
                        columnNames = "client_request_key"
                ),
                @UniqueConstraint(
                        name = "uk_payments_confirm_idempotency_key",
                        columnNames = "confirm_idempotency_key"
                ),
                @UniqueConstraint(
                        name = "uk_payments_provider_payment_key",
                        columnNames = {
                                "provider",
                                "provider_payment_key"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_payments_order_id_status",
                        columnList = "order_id, status"
                ),
                @Index(
                        name = "idx_payments_status_expires_at",
                        columnList = "status, expires_at"
                ),
                @Index(
                        name = "idx_payments_status_confirming_at",
                        columnList = "status, confirming_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "order_id",
            nullable = false
    )
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private PaymentStatus status;

    @Column(
            name = "merchant_payment_id",
            nullable = false,
            length = 100
    )
    private String merchantPaymentId;

    @Column(
            name = "client_request_key",
            nullable = false,
            length = 100
    )
    private String clientRequestKey;

    @Column(
            name = "confirm_idempotency_key",
            nullable = false,
            length = 100
    )
    private String confirmIdempotencyKey;

    @Column(
            name = "provider_payment_key",
            length = 200
    )
    private String providerPaymentKey;

    @Column(
            name = "provider_transaction_id",
            length = 200
    )
    private String providerTransactionId;

    @Column(nullable = false)
    private Long amount;

    @Column(
            nullable = false,
            length = 3
    )
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "easy_pay_provider",
            length = 30
    )
    private EasyPayProvider easyPayProvider;

    @Column(
            name = "provider_status",
            length = 100
    )
    private String providerStatus;

    @Column(
            name = "failure_code",
            length = 100
    )
    private String failureCode;

    @Column(
            name = "failure_message",
            length = 500
    )
    private String failureMessage;

    @Column(
            name = "requested_at",
            nullable = false
    )
    private LocalDateTime requestedAt;

    @Column(name = "confirming_at")
    private LocalDateTime confirmingAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(
            name = "expires_at",
            nullable = false
    )
    private LocalDateTime expiresAt;

    @Builder
    private Payment(
            Order order,
            PaymentProvider provider,
            String merchantPaymentId,
            String clientRequestKey,
            String confirmIdempotencyKey,
            Long amount,
            String currency,
            LocalDateTime requestedAt,
            LocalDateTime expiresAt
    ) {
        this.order = order;
        this.provider = provider;
        this.status = PaymentStatus.READY;
        this.merchantPaymentId = merchantPaymentId;
        this.clientRequestKey = clientRequestKey;
        this.confirmIdempotencyKey = confirmIdempotencyKey;
        this.amount = amount;
        this.currency = currency;
        this.requestedAt = requestedAt;
        this.expiresAt = expiresAt;
    }

    public static Payment createReady(
            Order order,
            PaymentProvider provider,
            String merchantPaymentId,
            String clientRequestKey,
            String confirmIdempotencyKey,
            Long amount,
            String currency,
            LocalDateTime requestedAt,
            LocalDateTime expiresAt
    ) {
        return Payment.builder()
                .order(order)
                .provider(provider)
                .merchantPaymentId(merchantPaymentId)
                .clientRequestKey(clientRequestKey)
                .confirmIdempotencyKey(confirmIdempotencyKey)
                .amount(amount)
                .currency(currency)
                .requestedAt(requestedAt)
                .expiresAt(expiresAt)
                .build();
    }

    public void startConfirm(
            String providerPaymentKey,
            LocalDateTime confirmingAt
    ) {
        this.providerPaymentKey = providerPaymentKey;
        this.status = PaymentStatus.CONFIRMING;
        this.confirmingAt = confirmingAt;
    }

    public void complete(
            String providerPaymentKey,
            String providerTransactionId,
            PaymentMethod method,
            EasyPayProvider easyPayProvider,
            String providerStatus,
            LocalDateTime approvedAt
    ) {
        this.providerPaymentKey = providerPaymentKey;
        this.providerTransactionId = providerTransactionId;
        this.method = method;
        this.easyPayProvider = easyPayProvider;
        this.providerStatus = providerStatus;
        this.status = PaymentStatus.PAID;
        this.approvedAt = approvedAt;
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void fail(
            String failureCode,
            String failureMessage,
            String providerStatus,
            LocalDateTime failedAt
    ) {
        this.status = PaymentStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.providerStatus = providerStatus;
        this.failedAt = failedAt;
    }

    public void expire() {
        this.status = PaymentStatus.EXPIRED;
    }

    public void cancelFromProvider(
            String providerStatus,
            LocalDateTime cancelledAt
    ) {
        this.status = PaymentStatus.CANCELED;
        this.providerStatus = providerStatus;
        this.cancelledAt = cancelledAt;
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void startCancel() {
        this.status = PaymentStatus.CANCELING;
    }

    public void cancelFailed() {
        this.status = PaymentStatus.PAID;
    }

    public void cancelBeforeApproval(LocalDateTime cancelledAt) {
        this.status = PaymentStatus.CANCELED;
        this.cancelledAt = cancelledAt;
    }
}
