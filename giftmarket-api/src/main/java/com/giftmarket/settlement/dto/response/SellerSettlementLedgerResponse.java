package com.giftmarket.settlement.dto.response;

import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerType;

import java.time.LocalDateTime;

public record SellerSettlementLedgerResponse(
        Long id,
        Long sellerOrderId,
        SettlementLedgerType type,
        long amount,
        String currency,
        LocalDateTime occurredAt,
        LocalDateTime eligibleAt,
        String reason
) {
    public static SellerSettlementLedgerResponse from(SettlementLedgerEntry entry) {
        return new SellerSettlementLedgerResponse(
                entry.getId(), entry.getSellerOrder().getId(), entry.getType(),
                entry.getAmount(), entry.getCurrency(), entry.getOccurredAt(),
                entry.getEligibleAt(), entry.getReason()
        );
    }
}
