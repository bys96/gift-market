package com.giftmarket.payment.service;

import com.giftmarket.payment.entity.PaymentProvider;

import java.time.LocalDateTime;

public record PaymentCancellationReconciliationStart(
        Action action,
        Long paymentId,
        Long cancellationId,
        PaymentProvider provider,
        String providerPaymentKey,
        String merchantPaymentId,
        Long amount,
        String currency,
        String reason,
        String idempotencyKey,
        LocalDateTime requestedAt
) {
    public enum Action {
        QUERY,
        COMPLETED
    }
}
