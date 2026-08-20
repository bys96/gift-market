package com.giftmarket.order.entity;

import com.giftmarket.seller.entity.Seller;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SellerOrderTest {

    @Test
    void createPaid_createsSellerProcessingUnitWithoutShippingData() {
        Order order = mock(Order.class);
        Seller seller = mock(Seller.class);

        SellerOrder sellerOrder = SellerOrder.createPendingPayment(order, seller);

        assertThat(sellerOrder.getOrder()).isSameAs(order);
        assertThat(sellerOrder.getSeller()).isSameAs(seller);
        assertThat(sellerOrder.getStatus())
                .isEqualTo(SellerOrderStatus.PENDING_PAYMENT);
        assertThat(sellerOrder.getShippingCompany()).isNull();
        assertThat(sellerOrder.getTrackingNumber()).isNull();
        assertThat(sellerOrder.getPreparedAt()).isNull();
        assertThat(sellerOrder.getShippedAt()).isNull();
        assertThat(sellerOrder.getDeliveredAt()).isNull();

        sellerOrder.markPaid();
        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.PAID);

        sellerOrder.cancel();
        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.CANCELLED);
    }

    @Test
    void followsPaidPreparingShippedDeliveredFlow() {
        SellerOrder sellerOrder = paidSellerOrder();
        LocalDateTime preparedAt = LocalDateTime.now();
        LocalDateTime shippedAt = preparedAt.plusHours(1);
        LocalDateTime deliveredAt = shippedAt.plusDays(1);

        sellerOrder.prepare(preparedAt);
        sellerOrder.markShipped(shippedAt);
        sellerOrder.synchronizeLegacyShippingSnapshot(
                "테스트택배", "1234567890", shippedAt, null
        );
        sellerOrder.markDelivered(deliveredAt);
        sellerOrder.synchronizeLegacyShippingSnapshot(
                "테스트택배", "1234567890", shippedAt, deliveredAt
        );

        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.DELIVERED);
        assertThat(sellerOrder.getPreparedAt()).isEqualTo(preparedAt);
        assertThat(sellerOrder.getShippingCompany()).isEqualTo("테스트택배");
        assertThat(sellerOrder.getTrackingNumber()).isEqualTo("1234567890");
        assertThat(sellerOrder.getShippedAt()).isEqualTo(shippedAt);
        assertThat(sellerOrder.getDeliveredAt()).isEqualTo(deliveredAt);
    }

    @Test
    void rejectsSkippedOrReversedTransitions() {
        SellerOrder paid = paidSellerOrder();
        assertThatThrownBy(() -> paid.markShipped(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);

        SellerOrder preparing = paidSellerOrder();
        preparing.prepare(LocalDateTime.now());
        assertThatThrownBy(() -> preparing.markDelivered(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);

        SellerOrder delivered = paidSellerOrder();
        delivered.prepare(LocalDateTime.now());
        delivered.markShipped(LocalDateTime.now());
        delivered.markDelivered(LocalDateTime.now());
        assertThatThrownBy(() -> delivered.markShipped(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pendingOrCancelledOrderCannotStartShipping() {
        SellerOrder pending = SellerOrder.createPendingPayment(
                mock(Order.class), mock(Seller.class)
        );
        assertThatThrownBy(() -> pending.prepare(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);

        SellerOrder cancelled = paidSellerOrder();
        cancelled.cancel();
        assertThatThrownBy(() -> cancelled.prepare(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    private SellerOrder paidSellerOrder() {
        SellerOrder sellerOrder = SellerOrder.createPendingPayment(
                mock(Order.class), mock(Seller.class)
        );
        sellerOrder.markPaid();
        return sellerOrder;
    }
}
