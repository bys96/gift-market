package com.giftmarket.payment.service;

import com.giftmarket.payment.dto.response.ExchangeShippingPaymentResponse;
import com.giftmarket.payment.entity.PaymentProvider;

public record ExchangeShippingPaymentStart(
        Action action, Long paymentId, PaymentProvider provider, String providerPaymentKey,
        String providerOrderId, long amount, String idempotencyKey,
        ExchangeShippingPaymentResponse response
) {
    public enum Action { CONFIRM, QUERY, COMPLETED }
}
