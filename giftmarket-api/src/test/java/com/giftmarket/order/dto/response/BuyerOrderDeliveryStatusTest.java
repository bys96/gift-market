package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BuyerOrderDeliveryStatusTest {

    @Test
    void resolvesPaidSellerOrderStates() {
        assertStatus(BuyerOrderDeliveryStatus.PAID, SellerOrderStatus.PAID);
        assertStatus(BuyerOrderDeliveryStatus.PREPARING, SellerOrderStatus.PREPARING);
        assertStatus(BuyerOrderDeliveryStatus.SHIPPING, SellerOrderStatus.SHIPPED);
        assertStatus(BuyerOrderDeliveryStatus.DELIVERED, SellerOrderStatus.DELIVERED);
    }

    @Test
    void resolvesMultipleSellerOrderStates() {
        assertStatus(
                BuyerOrderDeliveryStatus.PAID,
                SellerOrderStatus.PAID,
                SellerOrderStatus.PAID
        );
        assertStatus(
                BuyerOrderDeliveryStatus.PREPARING,
                SellerOrderStatus.PAID,
                SellerOrderStatus.PREPARING
        );
        assertStatus(
                BuyerOrderDeliveryStatus.SHIPPING,
                SellerOrderStatus.PREPARING,
                SellerOrderStatus.SHIPPED
        );
        assertStatus(
                BuyerOrderDeliveryStatus.SHIPPING,
                SellerOrderStatus.SHIPPED,
                SellerOrderStatus.DELIVERED
        );
        assertStatus(
                BuyerOrderDeliveryStatus.DELIVERED,
                SellerOrderStatus.DELIVERED,
                SellerOrderStatus.DELIVERED
        );
    }

    @Test
    void orderTerminalStatusTakesPriority() {
        List<SellerOrderStatus> delivered = List.of(SellerOrderStatus.DELIVERED);

        assertThat(BuyerOrderDeliveryStatus.resolve(
                OrderStatus.PENDING_PAYMENT, delivered
        )).isEqualTo(BuyerOrderDeliveryStatus.PAYMENT_PENDING);
        assertThat(BuyerOrderDeliveryStatus.resolve(
                OrderStatus.PAYMENT_FAILED, delivered
        )).isEqualTo(BuyerOrderDeliveryStatus.PAYMENT_FAILED);
        assertThat(BuyerOrderDeliveryStatus.resolve(
                OrderStatus.PAYMENT_EXPIRED, delivered
        )).isEqualTo(BuyerOrderDeliveryStatus.PAYMENT_EXPIRED);
        assertThat(BuyerOrderDeliveryStatus.resolve(
                OrderStatus.CANCELLED, delivered
        )).isEqualTo(BuyerOrderDeliveryStatus.CANCELLED);
    }

    @Test
    void partialCancelledDoesNotHideActiveDelivery() {
        assertStatus(
                BuyerOrderDeliveryStatus.DELIVERED,
                SellerOrderStatus.CANCELLED,
                SellerOrderStatus.DELIVERED
        );
    }

    private void assertStatus(
            BuyerOrderDeliveryStatus expected,
            SellerOrderStatus... statuses
    ) {
        assertThat(BuyerOrderDeliveryStatus.resolve(
                OrderStatus.PAID,
                List.of(statuses)
        )).isEqualTo(expected);
    }
}
