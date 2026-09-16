package com.giftmarket.settlement.dto.response;

import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementStatus;

import java.time.LocalDateTime;

public record SellerSettlementResponse(
        Long id,
        String settlementNumber,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        String currency,
        long totalProductSalesAmount,
        long totalShippingSalesAmount,
        long totalCancellationAmount,
        long totalReturnAmount,
        long totalCommissionAmount,
        long totalAdjustmentAmount,
        long settlementAmount,
        int ledgerEntryCount,
        SettlementStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt
) {
    public static SellerSettlementResponse from(Settlement settlement) {
        return new SellerSettlementResponse(
                settlement.getId(), settlement.getSettlementNumber(),
                settlement.getPeriodStart(), settlement.getPeriodEnd(), settlement.getCurrency(),
                settlement.getTotalProductSalesAmount(), settlement.getTotalShippingSalesAmount(),
                settlement.getTotalCancellationAmount(), settlement.getTotalReturnAmount(),
                settlement.getTotalCommissionAmount(), settlement.getTotalAdjustmentAmount(),
                settlement.getSettlementAmount(), settlement.getLedgerEntryCount(),
                settlement.getStatus(), settlement.getConfirmedAt(), settlement.getCreatedAt()
        );
    }
}
