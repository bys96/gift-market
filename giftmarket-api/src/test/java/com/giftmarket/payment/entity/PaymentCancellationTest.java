package com.giftmarket.payment.entity;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PaymentCancellationTest {

    @Test
    void rejectsProviderReasonLongerThanTwoHundredCharacters() {
        Order order = mock(Order.class);
        Payment payment = mock(Payment.class);
        OrderCancellation cancellation = mock(OrderCancellation.class);
        given(payment.getOrder()).willReturn(order);
        given(payment.getAmount()).willReturn(10_000L);
        given(cancellation.getOrder()).willReturn(order);

        assertThatThrownBy(() -> PaymentCancellation.createPartial(
                payment,
                cancellation,
                "client-key",
                "idempotency-key",
                1_000L,
                "가".repeat(201),
                LocalDateTime.now()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200자");
    }
}
