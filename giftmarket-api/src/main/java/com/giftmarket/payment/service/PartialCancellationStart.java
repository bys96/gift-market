package com.giftmarket.payment.service;

import com.giftmarket.payment.entity.PaymentProvider;

public record PartialCancellationStart(
        Action action,
        Long cancellationId,
        Long paymentId,
        Long paymentCancellationId,
        PaymentProvider provider,
        String providerPaymentKey,
        String merchantPaymentId,
        Long originalAmount,
        Long cancelAmount,
        String currency,
        String reason,
        String idempotencyKey
) {
    public enum Action { EXECUTE, WAITING_APPROVAL, COMPLETED }
}
