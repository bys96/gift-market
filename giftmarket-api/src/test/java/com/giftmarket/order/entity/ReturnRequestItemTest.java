package com.giftmarket.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReturnRequestItemTest {

    @Test
    void createsItemWithinReturnableQuantity() {
        SellerOrder sellerOrder = mock(SellerOrder.class);
        ReturnRequest returnRequest = mock(ReturnRequest.class);
        OrderItem orderItem = mock(OrderItem.class);

        when(returnRequest.getSellerOrder())
                .thenReturn(sellerOrder);
        when(orderItem.getSellerOrder())
                .thenReturn(sellerOrder);

        when(orderItem.getQuantity())
                .thenReturn(3);
        when(orderItem.getCanceledQuantity())
                .thenReturn(1);
        when(orderItem.getReturnedQuantity())
                .thenReturn(0);

        ReturnRequestItem item = ReturnRequestItem.create(
                returnRequest,
                orderItem,
                2
        );

        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getOrderItem()).isEqualTo(orderItem);
        assertThat(item.getRestockedQuantity()).isZero();
        assertThat(item.getInspectionResult()).isNull();
    }

    @Test
    void rejectsQuantityGreaterThanReturnableQuantity() {
        SellerOrder sellerOrder = mock(SellerOrder.class);
        ReturnRequest returnRequest = mock(ReturnRequest.class);
        OrderItem orderItem = mock(OrderItem.class);

        when(returnRequest.getSellerOrder())
                .thenReturn(sellerOrder);
        when(orderItem.getSellerOrder())
                .thenReturn(sellerOrder);

        when(orderItem.getQuantity())
                .thenReturn(3);
        when(orderItem.getCanceledQuantity())
                .thenReturn(1);
        when(orderItem.getReturnedQuantity())
                .thenReturn(1);

        assertThatThrownBy(() -> ReturnRequestItem.create(
                returnRequest,
                orderItem,
                2
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsItemFromDifferentSellerOrder() {
        ReturnRequest returnRequest = mock(ReturnRequest.class);
        OrderItem orderItem = mock(OrderItem.class);

        when(returnRequest.getSellerOrder())
                .thenReturn(mock(SellerOrder.class));
        when(orderItem.getSellerOrder())
                .thenReturn(mock(SellerOrder.class));

        assertThatThrownBy(() -> ReturnRequestItem.create(
                returnRequest,
                orderItem,
                1
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restockableItemCanIncreaseRestockedQuantity() {
        SellerOrder sellerOrder = mock(SellerOrder.class);
        ReturnRequest returnRequest = mock(ReturnRequest.class);
        OrderItem orderItem = mock(OrderItem.class);

        when(returnRequest.getSellerOrder())
                .thenReturn(sellerOrder);
        when(orderItem.getSellerOrder())
                .thenReturn(sellerOrder);

        when(orderItem.getQuantity())
                .thenReturn(2);
        when(orderItem.getCanceledQuantity())
                .thenReturn(0);
        when(orderItem.getReturnedQuantity())
                .thenReturn(0);

        ReturnRequestItem item = ReturnRequestItem.create(
                returnRequest,
                orderItem,
                2
        );

        item.inspect(ReturnInspectionResult.RESTOCKABLE);
        item.increaseRestockedQuantity(2);

        assertThat(item.getInspectionResult())
                .isEqualTo(ReturnInspectionResult.RESTOCKABLE);
        assertThat(item.getRestockedQuantity())
                .isEqualTo(2);
    }

    @Test
    void nonRestockableItemCannotRestoreStock() {
        SellerOrder sellerOrder = mock(SellerOrder.class);
        ReturnRequest returnRequest = mock(ReturnRequest.class);
        OrderItem orderItem = mock(OrderItem.class);

        when(returnRequest.getSellerOrder())
                .thenReturn(sellerOrder);
        when(orderItem.getSellerOrder())
                .thenReturn(sellerOrder);

        when(orderItem.getQuantity())
                .thenReturn(1);
        when(orderItem.getCanceledQuantity())
                .thenReturn(0);
        when(orderItem.getReturnedQuantity())
                .thenReturn(0);

        ReturnRequestItem item = ReturnRequestItem.create(
                returnRequest,
                orderItem,
                1
        );

        item.inspect(
                ReturnInspectionResult.NON_RESTOCKABLE
        );

        assertThatThrownBy(
                () -> item.increaseRestockedQuantity(1)
        ).isInstanceOf(IllegalStateException.class);
    }
}