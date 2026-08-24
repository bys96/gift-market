package com.giftmarket.payment.entity;

import com.giftmarket.order.entity.ExchangeRequest;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ExchangeShippingPaymentTest {
    private final ExchangeRequest exchange = mock(ExchangeRequest.class);
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 24, 12, 0);

    @Test
    void createsOneLogicalReadyPaymentWithFixedSnapshots() {
        ExchangeShippingPayment payment = ExchangeShippingPayment.create(
                exchange, 8_000, "EXCHANGE-SHIPPING-10", "EXCHANGE-SHIPPING-PAYMENT-10");
        assertThat(payment.getStatus()).isEqualTo(ExchangeShippingPaymentStatus.READY);
        assertThat(payment.getAmount()).isEqualTo(8_000);
        assertThat(payment.getProviderOrderId()).isEqualTo("EXCHANGE-SHIPPING-10");
        assertThat(payment.getIdempotencyKey()).isEqualTo("EXCHANGE-SHIPPING-PAYMENT-10");
        assertThat(payment.getAttemptSequence()).isEqualTo(1);
    }

    @Test
    void uncertainResultStaysRequestedAndExplicitFailureCanRetrySameAggregate() {
        ExchangeShippingPayment payment = ExchangeShippingPayment.create(exchange, 6_000, "order", "key");
        payment.request("payment-key-1", now);
        assertThat(payment.getStatus()).isEqualTo(ExchangeShippingPaymentStatus.REQUESTED);
        payment.fail("DECLINED", "declined", "ABORTED", now.plusSeconds(1));
        payment.prepareRetry("order-2", "key-2");
        payment.request("payment-key-2", now.plusSeconds(2));
        assertThat(payment.getStatus()).isEqualTo(ExchangeShippingPaymentStatus.REQUESTED);
        assertThat(payment.getProviderPaymentKey()).isEqualTo("payment-key-2");
        assertThat(payment.getProviderOrderId()).isEqualTo("order-2");
        assertThat(payment.getIdempotencyKey()).isEqualTo("key-2");
        assertThat(payment.getAttemptSequence()).isEqualTo(2);
        payment.succeed("payment-key-2", "DONE", now.plusSeconds(3));
        assertThat(payment.getStatus()).isEqualTo(ExchangeShippingPaymentStatus.SUCCEEDED);
    }

    @Test
    void zeroAmountCanSucceedWithoutProviderCall() {
        ExchangeShippingPayment payment = ExchangeShippingPayment.create(exchange, 0, "order", "key");
        payment.succeed(null, "ZERO_AMOUNT", now);
        assertThat(payment.getStatus()).isEqualTo(ExchangeShippingPaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderPaymentKey()).isNull();
    }

    @Test
    void negativeAmountIsRejected() {
        assertThatThrownBy(() -> ExchangeShippingPayment.create(exchange, -1, "order", "key"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lateSuccessRequiresCompensationInsteadOfNormalSuccess() {
        ExchangeShippingPayment payment = ExchangeShippingPayment.create(exchange, 6_000, "order", "key");
        payment.expire(now);
        payment.requireCompensation("late-key", "DONE", now.plusMinutes(1));
        assertThat(payment.getStatus()).isEqualTo(ExchangeShippingPaymentStatus.COMPENSATION_REQUIRED);
        assertThat(payment.getFailureCode()).isEqualTo("LATE_PAYMENT_SUCCESS");
        assertThatThrownBy(() -> payment.succeed("late-key", "DONE", now.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void succeededPaymentCannotExpire() {
        ExchangeShippingPayment payment = ExchangeShippingPayment.create(exchange, 6_000, "order", "key");
        payment.request("payment-key", now);
        payment.succeed("payment-key", "DONE", now.plusSeconds(1));
        assertThatThrownBy(() -> payment.expire(now.plusHours(24)))
                .isInstanceOf(IllegalStateException.class);
    }
}
