package com.giftmarket.settlement.service;

import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerType;
import com.giftmarket.settlement.exception.SettlementException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettlementLedgerAggregatorTest {

    private final SettlementLedgerAggregator aggregator = new SettlementLedgerAggregator();

    @Test
    void aggregatesAllLedgerTypesAndMatchesSignedSum() {
        SettlementAggregation result = aggregator.aggregate(List.of(
                entry(SettlementLedgerType.SALE_PRODUCT, 100_000L),
                entry(SettlementLedgerType.SALE_SHIPPING, 3_000L),
                entry(SettlementLedgerType.CANCELLATION_REFUND, -20_000L),
                entry(SettlementLedgerType.RETURN_REFUND, -10_000L),
                entry(SettlementLedgerType.COMMISSION, -10_000L),
                entry(SettlementLedgerType.COMMISSION_REVERSAL, 3_000L),
                entry(SettlementLedgerType.MANUAL_ADJUSTMENT, -500L)
        ));

        assertThat(result.totalProductSalesAmount()).isEqualTo(100_000L);
        assertThat(result.totalShippingSalesAmount()).isEqualTo(3_000L);
        assertThat(result.totalCancellationAmount()).isEqualTo(20_000L);
        assertThat(result.totalReturnAmount()).isEqualTo(10_000L);
        assertThat(result.totalCommissionAmount()).isEqualTo(7_000L);
        assertThat(result.totalAdjustmentAmount()).isEqualTo(-500L);
        assertThat(result.settlementAmount()).isEqualTo(65_500L);
        assertThat(result.ledgerEntryCount()).isEqualTo(7);
    }

    @Test
    void permitsNegativeSettlementAndReversalOnlyCommissionPeriod() {
        SettlementAggregation result = aggregator.aggregate(List.of(
                entry(SettlementLedgerType.RETURN_REFUND, -10_000L),
                entry(SettlementLedgerType.COMMISSION_REVERSAL, 1_000L)
        ));

        assertThat(result.totalCommissionAmount()).isEqualTo(-1_000L);
        assertThat(result.settlementAmount()).isEqualTo(-9_000L);
    }

    @Test
    void rejectsEmptyEntries() {
        assertThatThrownBy(() -> aggregator.aggregate(List.of()))
                .isInstanceOf(SettlementException.class);
    }

    @Test
    void rejectsOverflow() {
        assertThatThrownBy(() -> aggregator.aggregate(List.of(
                entry(SettlementLedgerType.SALE_PRODUCT, Long.MAX_VALUE),
                entry(SettlementLedgerType.SALE_SHIPPING, 1L)
        ))).isInstanceOf(SettlementException.class);
    }

    private SettlementLedgerEntry entry(SettlementLedgerType type, long amount) {
        SettlementLedgerEntry entry = mock(SettlementLedgerEntry.class);
        when(entry.getType()).thenReturn(type);
        when(entry.getAmount()).thenReturn(amount);
        when(entry.getCurrency()).thenReturn(SettlementLedgerEntry.CURRENCY_KRW);
        return entry;
    }
}
