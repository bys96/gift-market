package com.giftmarket.payment.service;

import com.giftmarket.payment.entity.PaymentProvider;

import java.time.LocalDateTime;

public record PartialCancellationReconciliationStart(
        Action action,
        Long paymentId,
        Long paymentCancellationId,
        Long orderCancellationId,
        PaymentProvider provider,
        String providerPaymentKey,
        String merchantPaymentId,
        Long originalAmount,
        Long cancelAmount,
        Long expectedRemainingAmount,
        String currency,
        String reason,
        String idempotencyKey,
        String providerTransactionKey,
        LocalDateTime requestedAt
) {
    public enum Action { QUERY, NO_OP }
}
