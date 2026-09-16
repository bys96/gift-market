package com.giftmarket.settlement.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record AdminSettlementGenerateRequest(
        @NotNull @Positive Long sellerId,
        @NotNull LocalDateTime periodStart,
        @NotNull LocalDateTime periodEnd,
        @NotNull LocalDateTime cutoff
) {
}
