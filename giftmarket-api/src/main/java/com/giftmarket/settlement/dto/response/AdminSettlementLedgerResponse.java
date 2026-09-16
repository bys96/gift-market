package com.giftmarket.settlement.dto.response;

import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerSourceType;
import com.giftmarket.settlement.entity.SettlementLedgerType;

import java.time.LocalDateTime;

public record AdminSettlementLedgerResponse(
        Long id,
        Long sellerOrderId,
        SettlementLedgerType type,
        long amount,
        String currency,
        LocalDateTime occurredAt,
        LocalDateTime eligibleAt,
        String reason,
        SettlementLedgerSourceType sourceType,
        Long sourceId,
        String sourceDetailKey,
        Integer commissionRateBps,
        Long commissionBaseAmount,
        Long adminUserId,
        Long reversalOfEntryId,
        LocalDateTime createdAt
) {
    public static AdminSettlementLedgerResponse from(SettlementLedgerEntry entry) {
        return new AdminSettlementLedgerResponse(
                entry.getId(), entry.getSellerOrder().getId(), entry.getType(),
                entry.getAmount(), entry.getCurrency(), entry.getOccurredAt(), entry.getEligibleAt(),
                entry.getReason(), entry.getSourceType(), entry.getSourceId(), entry.getSourceDetailKey(),
                entry.getCommissionRateBps(), entry.getCommissionBaseAmount(),
                entry.getAdminUser() == null ? null : entry.getAdminUser().getId(),
                entry.getReversalOfEntry() == null ? null : entry.getReversalOfEntry().getId(),
                entry.getCreatedAt()
        );
    }
}
