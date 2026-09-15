package com.giftmarket.settlement.service;

public record SettlementAggregation(
        long totalProductSalesAmount,
        long totalShippingSalesAmount,
        long totalCancellationAmount,
        long totalReturnAmount,
        long totalCommissionAmount,
        long totalAdjustmentAmount,
        long settlementAmount,
        int ledgerEntryCount
) {
}
