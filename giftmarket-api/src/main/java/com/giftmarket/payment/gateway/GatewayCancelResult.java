package com.giftmarket.payment.gateway;

import java.time.LocalDateTime;

public record GatewayCancelResult(GatewayPaymentStatus status, String providerPaymentKey,
                                  String providerTransactionId, String merchantPaymentId,
                                  Long amount, Long remainingAmount, String currency,
                                  String providerStatus, LocalDateTime canceledAt,
                                  Long canceledAmount, String cancellationStatus) {
    public GatewayCancelResult(GatewayPaymentStatus status, String providerPaymentKey,
                               String providerTransactionId, String merchantPaymentId,
                               Long amount, Long remainingAmount, String currency,
                               String providerStatus, LocalDateTime canceledAt) {
        this(status, providerPaymentKey, providerTransactionId, merchantPaymentId,
                amount, remainingAmount, currency, providerStatus, canceledAt, null, null);
    }

    public GatewayCancelResult(GatewayPaymentStatus status, String providerPaymentKey,
                               String providerTransactionId, String merchantPaymentId,
                               Long amount, Long remainingAmount, String currency,
                               String providerStatus, LocalDateTime canceledAt,
                               Long canceledAmount) {
        this(status, providerPaymentKey, providerTransactionId, merchantPaymentId,
                amount, remainingAmount, currency, providerStatus, canceledAt,
                canceledAmount, null);
    }
}
