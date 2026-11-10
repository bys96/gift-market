package com.giftmarket.global.util;

import com.giftmarket.global.exception.MoneyCalculationException;
import com.giftmarket.order.entity.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class MoneyMathTest {
    @Test
    void preservesSignedOptionDiscountAndExactNormalAmounts() {
        assertThat(MoneyMath.unitPrice(10_000L, -3_000L)).isEqualTo(7_000L);
        assertThat(MoneyMath.lineTotal(7_000L, 3)).isEqualTo(21_000L);
        assertThat(MoneyMath.add(21_000L, 3_000L)).isEqualTo(24_000L);
        assertThat(MoneyMath.add(Long.MAX_VALUE, 0L)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsLongOverflowInsteadOfCreatingNegativeOrderAmount() {
        assertThatThrownBy(() -> MoneyMath.add(Long.MAX_VALUE, 1L)).isInstanceOf(MoneyCalculationException.class);
        assertThatThrownBy(() -> MoneyMath.multiply(Long.MAX_VALUE, 2L)).isInstanceOf(MoneyCalculationException.class);
        assertThatThrownBy(() -> Order.createPendingPayment("overflow", null, Long.MAX_VALUE, 1L,
                "받는 분", "01012345678", "12345", "주소", null)).isInstanceOf(MoneyCalculationException.class);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "-1,0", "1000,-1000", "1000,-1001", "9223372036854775807,1"})
    void rejectsInvalidOrOverflowedFinalVariantPrice(long price, long additional) {
        assertThatThrownBy(() -> MoneyMath.unitPrice(price, additional)).isInstanceOf(MoneyCalculationException.class);
    }

    @Test
    void rejectsInvalidQuantityAndNegativeShippingFee() {
        assertThatThrownBy(() -> MoneyMath.lineTotal(1L, 0)).isInstanceOf(MoneyCalculationException.class);
        assertThatThrownBy(() -> MoneyMath.lineTotal(1L, -1)).isInstanceOf(MoneyCalculationException.class);
        assertThatThrownBy(() -> MoneyMath.nonNegative(-1L)).isInstanceOf(MoneyCalculationException.class);
    }
}
