package com.giftmarket.order.entity;

import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OrderItemCancellationQuantityTest {

    @Test
    void newOrderItemStartsWithNoCanceledQuantity() {
        OrderItem orderItem = createOrderItem(3);

        assertThat(orderItem.getCanceledQuantity()).isZero();
        assertThat(orderItem.getRemainingQuantity()).isEqualTo(3);
    }

    @Test
    void canceledQuantityAccumulatesWithoutChangingOriginalQuantity() {
        OrderItem orderItem = createOrderItem(3);

        orderItem.increaseCanceledQuantity(1);
        orderItem.increaseCanceledQuantity(1);

        assertThat(orderItem.getQuantity()).isEqualTo(3);
        assertThat(orderItem.getCanceledQuantity()).isEqualTo(2);
        assertThat(orderItem.getRemainingQuantity()).isEqualTo(1);
    }

    @Test
    void canceledQuantityCannotExceedOriginalQuantity() {
        OrderItem orderItem = createOrderItem(1);

        assertThatThrownBy(() -> orderItem.increaseCanceledQuantity(2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmsPartialAndRemainingCancellationWithoutChangingOriginalQuantity() {
        OrderItem orderItem = createOrderItem(2);

        orderItem.confirmCancellation(1);
        assertThat(orderItem.getCanceledQuantity()).isEqualTo(1);
        assertThat(orderItem.isFullyCanceled()).isFalse();

        orderItem.confirmCancellation(1);
        assertThat(orderItem.getQuantity()).isEqualTo(2);
        assertThat(orderItem.getCanceledQuantity()).isEqualTo(2);
        assertThat(orderItem.isFullyCanceled()).isTrue();
    }

    @Test
    void rejectsNonPositiveConfirmedQuantity() {
        OrderItem orderItem = createOrderItem(2);

        assertThatThrownBy(() -> orderItem.confirmCancellation(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderItem.confirmCancellation(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private OrderItem createOrderItem(int quantity) {
        return OrderItem.create(
                mock(Order.class),
                mock(Product.class),
                null,
                mock(Seller.class),
                mock(SellerOrder.class),
                null,
                "상품",
                null,
                "상점",
                null,
                null,
                10_000L,
                0L,
                quantity,
                true,
                0L
        );
    }
}
