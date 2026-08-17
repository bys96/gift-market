package com.giftmarket.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderCancellationItemTest {

    @Test
    void createsItemWithinRemainingQuantity() {
        SellerOrder sellerOrder = mock(SellerOrder.class);
        OrderCancellation cancellation = mock(OrderCancellation.class);
        OrderItem orderItem = mock(OrderItem.class);
        when(cancellation.getSellerOrder()).thenReturn(sellerOrder);
        when(orderItem.getSellerOrder()).thenReturn(sellerOrder);
        when(orderItem.getRemainingQuantity()).thenReturn(2);

        OrderCancellationItem cancellationItem = OrderCancellationItem.create(
                cancellation,
                orderItem,
                1
        );

        assertThat(cancellationItem.getQuantity()).isEqualTo(1);
        assertThat(cancellationItem.getOrderItem()).isEqualTo(orderItem);
    }

    @Test
    void rejectsQuantityGreaterThanRemainingQuantity() {
        SellerOrder sellerOrder = mock(SellerOrder.class);
        OrderCancellation cancellation = mock(OrderCancellation.class);
        OrderItem orderItem = mock(OrderItem.class);
        when(cancellation.getSellerOrder()).thenReturn(sellerOrder);
        when(orderItem.getSellerOrder()).thenReturn(sellerOrder);
        when(orderItem.getRemainingQuantity()).thenReturn(1);

        assertThatThrownBy(() -> OrderCancellationItem.create(cancellation, orderItem, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsItemFromDifferentSellerOrder() {
        OrderCancellation cancellation = mock(OrderCancellation.class);
        OrderItem orderItem = mock(OrderItem.class);
        when(cancellation.getSellerOrder()).thenReturn(mock(SellerOrder.class));
        when(orderItem.getSellerOrder()).thenReturn(mock(SellerOrder.class));

        assertThatThrownBy(() -> OrderCancellationItem.create(cancellation, orderItem, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
