package com.giftmarket.settlement.service;

import org.springframework.stereotype.Component;

@Component
public class SettlementCommissionCalculator {

    private static final long BASIS_POINT_DENOMINATOR = 10_000L;

    public long calculate(long baseAmount, int rateBps) {
        validateAmount(baseAmount, "수수료 기준금액은 음수일 수 없습니다.");
        validateRate(rateBps);

        try {
            long quotientAmount = baseAmount / BASIS_POINT_DENOMINATOR;
            long remainderAmount = baseAmount % BASIS_POINT_DENOMINATOR;
            long quotientCommission = Math.multiplyExact(quotientAmount, (long) rateBps);
            long remainderCommission = Math.multiplyExact(remainderAmount, (long) rateBps)
                    / BASIS_POINT_DENOMINATOR;
            return Math.addExact(quotientCommission, remainderCommission);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("수수료 금액을 안전하게 계산할 수 없습니다.", exception);
        }
    }

    public long calculateCurrentReversal(
            long cumulativeRefundedProductAmount,
            int originalRateBps,
            long alreadyReversedCommissionAmount,
            long originalCommissionAmount
    ) {
        validateAmount(
                cumulativeRefundedProductAmount,
                "누적 환불 상품금액은 음수일 수 없습니다."
        );
        validateAmount(
                alreadyReversedCommissionAmount,
                "기존 수수료 환입액은 음수일 수 없습니다."
        );
        validateAmount(
                originalCommissionAmount,
                "최초 수수료는 음수일 수 없습니다."
        );
        validateRate(originalRateBps);
        if (alreadyReversedCommissionAmount > originalCommissionAmount) {
            throw new IllegalArgumentException("기존 수수료 환입액이 최초 수수료를 초과합니다.");
        }

        long targetReversal = Math.min(
                calculate(cumulativeRefundedProductAmount, originalRateBps),
                originalCommissionAmount
        );
        if (targetReversal < alreadyReversedCommissionAmount) {
            throw new IllegalArgumentException("누적 환불액과 기존 수수료 환입액이 일치하지 않습니다.");
        }
        return targetReversal - alreadyReversedCommissionAmount;
    }

    private void validateAmount(long amount, String message) {
        if (amount < 0L) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateRate(int rateBps) {
        if (rateBps < 0 || rateBps > 10_000) {
            throw new IllegalArgumentException("수수료율은 0에서 10000bp 사이여야 합니다.");
        }
    }
}
