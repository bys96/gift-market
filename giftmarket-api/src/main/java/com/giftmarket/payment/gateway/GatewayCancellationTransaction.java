package com.giftmarket.payment.gateway;

import java.time.LocalDateTime;

public record GatewayCancellationTransaction(
        String providerTransactionId,
        Long amount,
        String reason,
        String status,
        LocalDateTime canceledAt,
        Long remainingAmount
) {
}
