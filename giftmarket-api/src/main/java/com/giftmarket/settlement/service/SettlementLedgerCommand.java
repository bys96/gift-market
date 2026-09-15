package com.giftmarket.settlement.service;

import java.time.LocalDateTime;

public record SettlementLedgerCommand(
        Long sellerId,
        Long sellerOrderId,
        long amount,
        Long sourceId,
        String sourceDetailKey,
        LocalDateTime occurredAt,
        LocalDateTime eligibleAt,
        Integer commissionRateBps,
        Long commissionBaseAmount
) {

    public SettlementLedgerCommand {
        if (sellerId == null || sellerId <= 0L
                || sellerOrderId == null || sellerOrderId <= 0L
                || sourceId == null || sourceId <= 0L) {
            throw new IllegalArgumentException("정산 원장 판매자, 판매자 주문, 근거 ID가 필요합니다.");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("정산 원장 생성 금액은 0보다 커야 합니다.");
        }
        if (sourceDetailKey == null || sourceDetailKey.isBlank()
                || sourceDetailKey.trim().length() > 100) {
            throw new IllegalArgumentException("정산 원장 근거 상세 키가 필요합니다.");
        }
        sourceDetailKey = sourceDetailKey.trim();
        if (occurredAt == null) {
            throw new IllegalArgumentException("정산 원장 발생 시각이 필요합니다.");
        }
        if (eligibleAt != null && eligibleAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("정산 가능 시각은 발생 시각보다 이전일 수 없습니다.");
        }
    }

    public static SettlementLedgerCommand standard(
            Long sellerId,
            Long sellerOrderId,
            long amount,
            Long sourceId,
            String sourceDetailKey,
            LocalDateTime occurredAt,
            LocalDateTime eligibleAt
    ) {
        return new SettlementLedgerCommand(
                sellerId,
                sellerOrderId,
                amount,
                sourceId,
                sourceDetailKey,
                occurredAt,
                eligibleAt,
                null,
                null
        );
    }

    public static SettlementLedgerCommand commission(
            Long sellerId,
            Long sellerOrderId,
            long amount,
            Long sourceId,
            String sourceDetailKey,
            LocalDateTime occurredAt,
            LocalDateTime eligibleAt,
            int commissionRateBps,
            long commissionBaseAmount
    ) {
        return new SettlementLedgerCommand(
                sellerId,
                sellerOrderId,
                amount,
                sourceId,
                sourceDetailKey,
                occurredAt,
                eligibleAt,
                commissionRateBps,
                commissionBaseAmount
        );
    }
}
