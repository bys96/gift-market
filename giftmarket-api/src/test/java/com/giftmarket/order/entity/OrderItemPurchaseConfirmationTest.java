package com.giftmarket.order.entity;

import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OrderItemPurchaseConfirmationTest {
    @Test void confirmsOwnedQuantityAndBlocksCustomerServiceQuantities() {
        OrderItem item = item(3);
        item.confirmCancellation(1);
        item.confirmPurchase(2);
        assertThat(item.getConfirmedQuantity()).isEqualTo(2);
        assertThat(item.getRemainingQuantity()).isZero();
        assertThat(item.getReturnableQuantity()).isZero();
        assertThat(item.getExchangeableQuantity()).isZero();
        assertThatThrownBy(() -> item.confirmCancellation(1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void completedExchangeCanStillBePurchaseConfirmed() {
        OrderItem item = item(2);
        item.confirmExchange(1);
        item.confirmPurchase(2);
        assertThat(item.getConfirmedQuantity()).isEqualTo(2);
    }

    @Test void confirmationCannotExceedCanceledAndReturnedAdjustedOwnership() {
        OrderItem item = item(3);
        item.confirmCancellation(1);
        item.confirmReturn(1);
        assertThatThrownBy(() -> item.confirmPurchase(2)).isInstanceOf(IllegalArgumentException.class);
    }

    private OrderItem item(int quantity) {
        return OrderItem.create(mock(Order.class), mock(Product.class), null, mock(Seller.class),
                mock(SellerOrder.class), null, "상품", null, "상점", null, null,
                1000L, 0L, quantity, true, 0L, 0L, 0L);
    }
}
