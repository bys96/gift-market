package com.giftmarket.payment.service;

import com.giftmarket.payment.dto.response.PaymentResponse;
import com.giftmarket.payment.entity.PaymentProvider;

record PaymentConfirmStart(
        Action action,
        PaymentProvider provider,
        String providerPaymentKey,
        String merchantPaymentId,
        Long amount,
        String currency,
        String confirmIdempotencyKey,
        PaymentResponse response
) {
    enum Action {
        CONFIRM,
        QUERY,
        COMPLETED
    }
}
