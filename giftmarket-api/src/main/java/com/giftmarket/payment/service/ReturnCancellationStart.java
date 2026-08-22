package com.giftmarket.payment.service;

import com.giftmarket.payment.entity.PaymentProvider;

public record ReturnCancellationStart(
        Action action, Long returnRequestId, Long paymentId, Long paymentCancellationId,
        PaymentProvider provider, String providerPaymentKey, String merchantPaymentId,
        Long originalAmount, Long cancelAmount, String currency, String reason, String idempotencyKey
) {
    public enum Action { EXECUTE, RECONCILE, SUCCEEDED, ZERO_REFUND }
}
