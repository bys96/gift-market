package com.giftmarket.order.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderCancellationTest {

    @Test
    void requestedCancellationCanProceedAndComplete() {
        Order order = mock(Order.class);
        SellerOrder sellerOrder = mock(SellerOrder.class);
        when(sellerOrder.getOrder()).thenReturn(order);
        LocalDateTime now = LocalDateTime.now();

        OrderCancellation cancellation = OrderCancellation.createRequested(
                order,
                sellerOrder,
                "request-key",
                "단순 변심",
                now
        );

        cancellation.startProcessing(now.plusSeconds(1));
        cancellation.complete(now.plusSeconds(2));

        assertThat(cancellation.getStatus()).isEqualTo(OrderCancellationStatus.COMPLETED);
        assertThat(cancellation.getCompletedAt()).isEqualTo(now.plusSeconds(2));
    }

    @Test
    void cancellationRejectsSellerOrderFromDifferentOrder() {
        Order order = mock(Order.class);
        SellerOrder sellerOrder = mock(SellerOrder.class);
        when(sellerOrder.getOrder()).thenReturn(mock(Order.class));

        assertThatThrownBy(() -> OrderCancellation.createRequested(
                order,
                sellerOrder,
                "request-key",
                "단순 변심",
                LocalDateTime.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void processingCancellationCannotBeRejected() {
        Order order = mock(Order.class);
        SellerOrder sellerOrder = mock(SellerOrder.class);
        when(sellerOrder.getOrder()).thenReturn(order);
        OrderCancellation cancellation = OrderCancellation.createProcessing(
                order,
                sellerOrder,
                "request-key",
                "단순 변심",
                LocalDateTime.now()
        );

        assertThatThrownBy(() -> cancellation.reject("거절", LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }
}
