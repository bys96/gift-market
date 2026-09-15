package com.giftmarket.settlement.service;

import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.exception.SettlementException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SettlementLedgerAggregator {

    public SettlementAggregation aggregate(List<SettlementLedgerEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new SettlementException("정산 집계에는 하나 이상의 원장이 필요합니다.");
        }
        long productSales = 0L;
        long shippingSales = 0L;
        long cancellations = 0L;
        long returns = 0L;
        long commission = 0L;
        long adjustment = 0L;
        long ledgerSum = 0L;

        try {
            for (SettlementLedgerEntry entry : entries) {
                if (entry == null || entry.getType() == null || entry.getAmount() == null
                        || !SettlementLedgerEntry.CURRENCY_KRW.equals(entry.getCurrency())) {
                    throw new SettlementException("정산 원장 payload가 올바르지 않습니다.");
                }
                long amount = entry.getAmount();
                validateSign(entry);
                ledgerSum = Math.addExact(ledgerSum, amount);
                switch (entry.getType()) {
                    case SALE_PRODUCT -> productSales = Math.addExact(productSales, amount);
                    case SALE_SHIPPING -> shippingSales = Math.addExact(shippingSales, amount);
                    case CANCELLATION_REFUND -> cancellations = Math.addExact(
                            cancellations,
                            Math.negateExact(amount)
                    );
                    case RETURN_REFUND -> returns = Math.addExact(
                            returns,
                            Math.negateExact(amount)
                    );
                    case COMMISSION -> commission = Math.addExact(
                            commission,
                            Math.negateExact(amount)
                    );
                    case COMMISSION_REVERSAL -> commission = Math.subtractExact(
                            commission,
                            amount
                    );
                    case MANUAL_ADJUSTMENT -> adjustment = Math.addExact(adjustment, amount);
                }
            }
            long calculated = Math.addExact(productSales, shippingSales);
            calculated = Math.subtractExact(calculated, cancellations);
            calculated = Math.subtractExact(calculated, returns);
            calculated = Math.subtractExact(calculated, commission);
            calculated = Math.addExact(calculated, adjustment);
            if (calculated != ledgerSum) {
                throw new SettlementException("정산 snapshot 합계와 원장 합계가 일치하지 않습니다.");
            }
            return new SettlementAggregation(
                    productSales,
                    shippingSales,
                    cancellations,
                    returns,
                    commission,
                    adjustment,
                    calculated,
                    entries.size()
            );
        } catch (ArithmeticException exception) {
            throw new SettlementException("정산 원장 금액을 안전하게 집계할 수 없습니다.");
        }
    }

    private void validateSign(SettlementLedgerEntry entry) {
        long amount = entry.getAmount();
        boolean valid = switch (entry.getType()) {
            case SALE_PRODUCT, SALE_SHIPPING, COMMISSION_REVERSAL -> amount > 0L;
            case CANCELLATION_REFUND, RETURN_REFUND, COMMISSION -> amount < 0L;
            case MANUAL_ADJUSTMENT -> amount != 0L;
        };
        if (!valid) {
            throw new SettlementException("정산 원장 유형과 금액 부호가 일치하지 않습니다.");
        }
    }
}
