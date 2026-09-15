package com.giftmarket.settlement.service;

import java.time.LocalDateTime;

public record SettlementGenerationCommand(
        Long sellerId,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        LocalDateTime cutoff
) {
}
