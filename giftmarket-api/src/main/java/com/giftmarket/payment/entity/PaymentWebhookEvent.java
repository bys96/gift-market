package com.giftmarket.payment.entity;

import com.giftmarket.global.entity.BaseEntity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "payment_webhook_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_webhook_provider_external_id",
                columnNames = {"provider", "external_event_id"}
        ),
        indexes = @Index(
                name = "idx_payment_webhook_status_created_at",
                columnList = "status, created_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentWebhookEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentProvider provider;

    @Column(name = "external_event_id", nullable = false, length = 200)
    private String externalEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentWebhookStatus status;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    public static PaymentWebhookEvent create(
            PaymentProvider provider,
            String externalEventId,
            String eventType,
            LocalDateTime receivedAt
    ) {
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.provider = provider;
        event.externalEventId = externalEventId;
        event.eventType = eventType;
        event.status = PaymentWebhookStatus.PROCESSING;
        event.receivedAt = receivedAt;
        return event;
    }

    public void restart(LocalDateTime receivedAt) {
        this.status = PaymentWebhookStatus.PROCESSING;
        this.receivedAt = receivedAt;
        this.processedAt = null;
        this.failureReason = null;
    }

    public void processed(Payment payment, LocalDateTime processedAt) {
        this.payment = payment;
        this.status = PaymentWebhookStatus.PROCESSED;
        this.processedAt = processedAt;
        this.failureReason = null;
    }

    public void ignored(String reason, LocalDateTime processedAt) {
        finish(PaymentWebhookStatus.IGNORED, null, reason, processedAt);
    }

    public void rejected(Payment payment, String reason, LocalDateTime processedAt) {
        finish(PaymentWebhookStatus.REJECTED, payment, reason, processedAt);
    }

    public void retryableFailure(String reason, LocalDateTime processedAt) {
        finish(PaymentWebhookStatus.RETRYABLE_FAILED, payment, reason, processedAt);
    }

    private void finish(
            PaymentWebhookStatus status,
            Payment payment,
            String reason,
            LocalDateTime processedAt
    ) {
        this.status = status;
        this.payment = payment;
        this.failureReason = reason;
        this.processedAt = processedAt;
    }
}
