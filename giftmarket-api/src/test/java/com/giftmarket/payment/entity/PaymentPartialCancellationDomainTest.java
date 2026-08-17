package com.giftmarket.payment.entity;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PaymentPartialCancellationDomainTest {

    @Test
    void supportsPartialThenFullCancellationStateTransitions() {
        Payment payment = paidPayment(mock(Order.class), 30_000L);

        payment.markPartiallyCanceled("PARTIAL_CANCELED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_CANCELED);
        assertThat(payment.isRefundableState()).isTrue();

        payment.markPartiallyCanceled("PARTIAL_CANCELED");
        payment.markFullyCanceled("CANCELED", LocalDateTime.now());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.isRefundableState()).isFalse();
        assertThatThrownBy(() -> payment.markPartiallyCanceled("PARTIAL_CANCELED"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createsLegacyFullAndLinkedPartialPaymentCancellation() {
        Order order = mock(Order.class);
        Payment payment = paidPayment(order, 30_000L);
        OrderCancellation orderCancellation = mock(OrderCancellation.class);
        given(orderCancellation.getOrder()).willReturn(order);

        PaymentCancellation full = PaymentCancellation.create(
                payment, "full-client", "full-idempotency", "reason", LocalDateTime.now()
        );
        PaymentCancellation partial = PaymentCancellation.createPartial(
                payment, orderCancellation, "partial-client", "partial-idempotency",
                10_000L, "partial reason", LocalDateTime.now()
        );

        assertThat(full.getType()).isEqualTo(PaymentCancellationType.FULL);
        assertThat(full.getOrderCancellation()).isNull();
        assertThat(full.getAmount()).isEqualTo(30_000L);
        assertThat(partial.getType()).isEqualTo(PaymentCancellationType.PARTIAL);
        assertThat(partial.getOrderCancellation()).isSameAs(orderCancellation);
        assertThat(partial.getAmount()).isEqualTo(10_000L);
        assertThat(partial.getIdempotencyKey()).isEqualTo("partial-idempotency");
    }

    private Payment paidPayment(Order order, long amount) {
        Payment payment = Payment.createReady(
                order, PaymentProvider.TOSS, "merchant", "client", "confirm",
                amount, "KRW", LocalDateTime.now(), LocalDateTime.now().plusMinutes(30)
        );
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.complete("payment-key", "transaction", PaymentMethod.CARD,
                null, "DONE", LocalDateTime.now());
        return payment;
    }
}
