package com.giftmarket.payment.entity;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.ReturnRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PaymentCancellationTest {

    @Test
    void createsPartialCancellationLinkedOnlyToReturnRequest() {
        Order order = mock(Order.class);
        Payment payment = mock(Payment.class);
        ReturnRequest returnRequest = mock(ReturnRequest.class);
        given(payment.getOrder()).willReturn(order);
        given(payment.getAmount()).willReturn(10_000L);
        given(returnRequest.getOrder()).willReturn(order);

        PaymentCancellation value = PaymentCancellation.createReturnPartial(
                payment, returnRequest, "RETURN-REFUND-1", "RETURN-REFUND-1",
                3_000L, "return partial refund", LocalDateTime.now());

        assertThat(value.getType()).isEqualTo(PaymentCancellationType.PARTIAL);
        assertThat(value.getReturnRequest()).isSameAs(returnRequest);
        assertThat(value.getOrderCancellation()).isNull();
        assertThat(value.getStatus()).isEqualTo(PaymentCancellationStatus.REQUESTED);
        assertThat(value.getAmount()).isEqualTo(3_000L);
    }

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
