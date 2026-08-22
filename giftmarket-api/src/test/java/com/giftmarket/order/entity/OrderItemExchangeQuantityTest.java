package com.giftmarket.order.entity;

import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OrderItemExchangeQuantityTest {
    @Test
    void newItemStartsWithZeroExchangedQuantity() {
        OrderItem item = item(3);
        assertThat(item.getExchangedQuantity()).isZero();
        assertThat(item.getExchangeableQuantity()).isEqualTo(3);
    }

    @Test
    void confirmedExchangeAccumulatesAndReducesAvailableQuantity() {
        OrderItem item = item(3);
        item.confirmCancellation(1);
        item.confirmReturn(1);
        item.confirmExchange(1);
        assertThat(item.getExchangedQuantity()).isEqualTo(1);
        assertThat(item.getExchangeableQuantity()).isZero();
        assertThat(item.isFullyReturnedOrCanceled()).isTrue();
    }

    @Test
    void canceledReturnedAndExchangedCannotExceedOriginalQuantity() {
        OrderItem item = item(3);
        item.confirmCancellation(1);
        item.confirmReturn(1);
        assertThatThrownBy(() -> item.confirmExchange(2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.confirmCancellation(2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.confirmReturn(2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exchangeQuantityRejectsNonPositiveAndOverflow() {
        OrderItem item = item(Integer.MAX_VALUE);
        assertThatThrownBy(() -> item.confirmExchange(0))
                .isInstanceOf(IllegalArgumentException.class);
        ReflectionTestUtils.setField(item, "exchangedQuantity", Integer.MAX_VALUE);
        assertThatThrownBy(() -> item.confirmExchange(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overflow");
    }

    private OrderItem item(int quantity) {
        return OrderItem.create(
                mock(Order.class), mock(Product.class), null, mock(Seller.class),
                mock(SellerOrder.class), null, "상품", null, "상점", null,
                null, 10_000L, 0L, quantity, true, 0L, 3_000L, 6_000L
        );
    }
}
