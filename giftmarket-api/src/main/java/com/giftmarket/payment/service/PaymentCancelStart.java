package com.giftmarket.payment.service;

import com.giftmarket.order.dto.response.OrderCancelResponse;
import com.giftmarket.payment.entity.PaymentProvider;
import java.time.LocalDateTime;

public record PaymentCancelStart(Action action, Long paymentId, Long cancellationId,
                                 PaymentProvider provider, String providerPaymentKey,
                                 String merchantPaymentId, Long amount, String currency,
                                 String reason, String idempotencyKey, LocalDateTime requestedAt,
                                 OrderCancelResponse response) {
    public enum Action { CANCEL, QUERY, COMPLETED }
}
