package com.giftmarket.settlement.dto.response;

public record SellerSettlementSummaryResponse(
        String currency,
        long unassignedAmount,
        long eligibleAmount,
        long holdAmount,
        long awaitingEligibilityAmount
) {
}
