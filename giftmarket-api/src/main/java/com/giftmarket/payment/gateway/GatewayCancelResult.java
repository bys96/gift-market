package com.giftmarket.payment.gateway;

import java.time.LocalDateTime;

public record GatewayCancelResult(GatewayPaymentStatus status, String providerPaymentKey,
                                  String providerTransactionId, String merchantPaymentId,
                                  Long amount, Long remainingAmount, String currency,
                                  String providerStatus, LocalDateTime canceledAt) {
}
