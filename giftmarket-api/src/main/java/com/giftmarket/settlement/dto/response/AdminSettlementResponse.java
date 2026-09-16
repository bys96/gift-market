package com.giftmarket.settlement.dto.response;

import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementStatus;

import java.time.LocalDateTime;

public record AdminSettlementResponse(
        Long settlementId,
        String settlementNumber,
        Long sellerId,
        String storeName,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        String currency,
        long productSalesAmount,
        long shippingSalesAmount,
        long cancellationAmount,
        long returnAmount,
        long commissionAmount,
        long adjustmentAmount,
        long settlementAmount,
        int ledgerEntryCount,
        SettlementStatus status,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt
) {
    public static AdminSettlementResponse from(Settlement settlement) {
        return new AdminSettlementResponse(
                settlement.getId(), settlement.getSettlementNumber(),
                settlement.getSeller().getId(), settlement.getSeller().getStoreName(),
                settlement.getPeriodStart(), settlement.getPeriodEnd(), settlement.getCurrency(),
                settlement.getTotalProductSalesAmount(), settlement.getTotalShippingSalesAmount(),
                settlement.getTotalCancellationAmount(), settlement.getTotalReturnAmount(),
                settlement.getTotalCommissionAmount(), settlement.getTotalAdjustmentAmount(),
                settlement.getSettlementAmount(), settlement.getLedgerEntryCount(),
                settlement.getStatus(), settlement.getCreatedAt(), settlement.getConfirmedAt()
        );
    }
}
