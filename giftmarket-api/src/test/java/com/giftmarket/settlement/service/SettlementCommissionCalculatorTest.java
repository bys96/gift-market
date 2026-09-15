package com.giftmarket.settlement.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementCommissionCalculatorTest {

    private final SettlementCommissionCalculator calculator = new SettlementCommissionCalculator();

    @Test
    void calculatesZeroTenAndOneHundredPercent() {
        assertThat(calculator.calculate(12_345L, 0)).isZero();
        assertThat(calculator.calculate(12_345L, 1_000)).isEqualTo(1_234L);
        assertThat(calculator.calculate(12_345L, 10_000)).isEqualTo(12_345L);
    }

    @Test
    void truncatesSubWonAmount() {
        assertThat(calculator.calculate(999L, 500)).isEqualTo(49L);
        assertThat(calculator.calculate(9L, 1_000)).isZero();
    }

    @Test
    void calculatesCumulativePartialRefundReversalWithoutPartitionRoundingLoss() {
        long originalCommission = calculator.calculate(10_001L, 1_000);

        long first = calculator.calculateCurrentReversal(3_333L, 1_000, 0L, originalCommission);
        long second = calculator.calculateCurrentReversal(6_666L, 1_000, first, originalCommission);
        long last = calculator.calculateCurrentReversal(
                10_001L,
                1_000,
                Math.addExact(first, second),
                originalCommission
        );

        assertThat(first).isEqualTo(333L);
        assertThat(second).isEqualTo(333L);
        assertThat(last).isEqualTo(334L);
        assertThat(Math.addExact(Math.addExact(first, second), last))
                .isEqualTo(originalCommission);
    }

    @Test
    void capsReversalAtOriginalCommission() {
        assertThat(calculator.calculateCurrentReversal(
                100_000L,
                1_000,
                0L,
                500L
        )).isEqualTo(500L);
    }

    @Test
    void rejectsAlreadyReversedAmountAboveOriginalCommission() {
        assertThatThrownBy(() -> calculator.calculateCurrentReversal(
                10_000L,
                1_000,
                1_001L,
                1_000L
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void safelyCalculatesLongMaximumWithoutMultiplicationOverflow() {
        assertThat(calculator.calculate(Long.MAX_VALUE, 10_000))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsInvalidInputInsteadOfAllowingUnsafeCalculation() {
        assertThatThrownBy(() -> calculator.calculate(-1L, 1_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(1_000L, 10_001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
