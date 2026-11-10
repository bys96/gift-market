package com.giftmarket.cart.dto.response;

import com.giftmarket.global.exception.MoneyCalculationException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CartMoneyCalculationTest {
    @Test
    void calculatesNormalMultipleProductsWithExistingPerItemShippingPolicy() {
        var response = CartResponse.from(List.of(item(10_000L, 2, 3_000L), item(20_000L, 1, 0L)));
        assertThat(response.getTotalProductPrice()).isEqualTo(40_000L);
        assertThat(response.getTotalShippingFee()).isEqualTo(3_000L);
        assertThat(response.getTotalPrice()).isEqualTo(43_000L);
    }

    @Test
    void refusesProductShippingSumAndQuantityMultiplicationOverflow() {
        assertThatThrownBy(() -> CartResponse.from(List.of(item(Long.MAX_VALUE, 1, 1L))))
                .isInstanceOf(MoneyCalculationException.class);
        assertThatThrownBy(() -> CartResponse.from(List.of(item(Long.MAX_VALUE, 2, 0L))))
                .isInstanceOf(MoneyCalculationException.class);
        assertThatThrownBy(() -> CartResponse.from(List.of(item(Long.MAX_VALUE, 1, 0L), item(1L, 1, 0L))))
                .isInstanceOf(MoneyCalculationException.class);
    }

    private CartItemResponse item(long price, int quantity, long shippingFee) {
        return CartItemResponse.builder().price(price).quantity(quantity).shippingFee(shippingFee)
                .freeShipping(false).purchasable(true).build();
    }
}
