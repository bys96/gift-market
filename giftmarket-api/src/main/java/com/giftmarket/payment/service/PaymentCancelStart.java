package com.giftmarket.payment.service;

import com.giftmarket.order.dto.response.OrderCancelResponse;
import com.giftmarket.payment.entity.PaymentProvider;

public record PaymentCancelStart(Action action, Long paymentId, Long cancellationId,
                                 PaymentProvider provider, String providerPaymentKey,
                                 String merchantPaymentId, Long amount, String currency,
                                 String reason, String idempotencyKey,
                                 OrderCancelResponse response) {
    public enum Action { CANCEL, QUERY, COMPLETED }
}
