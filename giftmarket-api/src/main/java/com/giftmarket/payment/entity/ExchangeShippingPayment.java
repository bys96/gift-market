package com.giftmarket.payment.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.order.entity.ExchangeRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "exchange_shipping_payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_exchange_shipping_payments_request", columnNames = "exchange_request_id"),
        @UniqueConstraint(name = "uk_exchange_shipping_payments_order_id", columnNames = "provider_order_id"),
        @UniqueConstraint(name = "uk_exchange_shipping_payments_idempotency", columnNames = "idempotency_key")
}, indexes = @Index(name = "idx_exchange_shipping_payments_status_requested", columnList = "status, requested_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeShippingPayment extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_request_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exchange_shipping_payments_request"))
    private ExchangeRequest exchangeRequest;
    @Column(nullable = false) private long amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ExchangeShippingPaymentStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentProvider provider;
    @Column(name = "provider_payment_key", length = 200) private String providerPaymentKey;
    @Column(name = "provider_order_id", nullable = false, length = 100) private String providerOrderId;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @Column(name = "attempt_sequence", nullable = false) private int attemptSequence;
    @Column(name = "provider_status", length = 100) private String providerStatus;
    @Column(name = "requested_at") private LocalDateTime requestedAt;
    @Column(name = "succeeded_at") private LocalDateTime succeededAt;
    @Column(name = "failed_at") private LocalDateTime failedAt;
    @Column(name = "expired_at") private LocalDateTime expiredAt;
    @Column(name = "failure_code", length = 100) private String failureCode;
    @Column(name = "failure_message", length = 500) private String failureMessage;

    public static ExchangeShippingPayment create(ExchangeRequest request, long amount,
                                                   String providerOrderId, String idempotencyKey) {
        if (request == null || amount < 0) throw new IllegalArgumentException("교환 배송비를 확인해주세요.");
        ExchangeShippingPayment payment = new ExchangeShippingPayment();
        payment.exchangeRequest = request; payment.amount = amount; payment.provider = PaymentProvider.TOSS;
        payment.status = ExchangeShippingPaymentStatus.READY; payment.attemptSequence = 1;
        payment.providerOrderId = requireText(providerOrderId); payment.idempotencyKey = requireText(idempotencyKey);
        return payment;
    }

    public void request(String paymentKey, LocalDateTime now) {
        if (status != ExchangeShippingPaymentStatus.READY
                && status != ExchangeShippingPaymentStatus.REQUESTED) throw new IllegalStateException("현재 결제 상태에서는 승인할 수 없습니다.");
        if (status == ExchangeShippingPaymentStatus.REQUESTED && providerPaymentKey != null
                && !providerPaymentKey.equals(paymentKey)) throw new IllegalStateException("결제 식별정보가 일치하지 않습니다.");
        providerPaymentKey = requireText(paymentKey); status = ExchangeShippingPaymentStatus.REQUESTED;
        if (requestedAt == null) requestedAt = now; failureCode = null; failureMessage = null;
    }

    public void succeed(String paymentKey, String providerStatus, LocalDateTime now) {
        if (status == ExchangeShippingPaymentStatus.SUCCEEDED) return;
        if (status != ExchangeShippingPaymentStatus.REQUESTED && amount != 0) throw new IllegalStateException("결제 성공을 반영할 수 없는 상태입니다.");
        if (paymentKey != null) providerPaymentKey = paymentKey;
        status = ExchangeShippingPaymentStatus.SUCCEEDED; this.providerStatus = providerStatus; succeededAt = now;
        failureCode = null; failureMessage = null;
    }

    public void fail(String code, String message, String providerStatus, LocalDateTime now) {
        if (status != ExchangeShippingPaymentStatus.REQUESTED) return;
        status = ExchangeShippingPaymentStatus.FAILED; failureCode = code; failureMessage = message;
        this.providerStatus = providerStatus; failedAt = now;
    }

    public void prepareRetry(String providerOrderId, String idempotencyKey) {
        if (status != ExchangeShippingPaymentStatus.FAILED) {
            throw new IllegalStateException("명시적으로 실패한 결제만 새 결제 시도를 준비할 수 있습니다.");
        }
        attemptSequence = Math.addExact(attemptSequence, 1);
        this.providerOrderId = requireText(providerOrderId);
        this.idempotencyKey = requireText(idempotencyKey);
        providerPaymentKey = null;
        providerStatus = null;
        requestedAt = null;
        status = ExchangeShippingPaymentStatus.READY;
        failureCode = null;
        failureMessage = null;
    }

    public void expire(LocalDateTime now) {
        if (status != ExchangeShippingPaymentStatus.READY && status != ExchangeShippingPaymentStatus.FAILED) throw new IllegalStateException("확정되지 않은 결제는 만료할 수 없습니다.");
        status = ExchangeShippingPaymentStatus.EXPIRED; expiredAt = now;
    }

    public void requireCompensation(String paymentKey, String providerStatus, LocalDateTime now) {
        providerPaymentKey = paymentKey; this.providerStatus = providerStatus;
        status = ExchangeShippingPaymentStatus.COMPENSATION_REQUIRED; succeededAt = now;
        failureCode = "LATE_PAYMENT_SUCCESS";
        failureMessage = "만료 취소 후 결제 성공이 확인되어 운영자 취소가 필요합니다.";
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("결제 식별정보가 필요합니다.");
        return value;
    }
}
