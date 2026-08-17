package com.giftmarket.payment.service;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.entity.PaymentCancellationType;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartialPaymentCancellationPreparationServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentCancellationRepository paymentCancellationRepository;
    @Mock OrderCancellationRepository orderCancellationRepository;
    @Mock PaymentRefundBalanceService refundBalanceService;
    private PartialPaymentCancellationPreparationService service;
    private Order order;
    private Payment payment;
    private OrderCancellation orderCancellation;

    @BeforeEach
    void setUp() {
        service = new PartialPaymentCancellationPreparationService(
                paymentRepository, paymentCancellationRepository,
                orderCancellationRepository, refundBalanceService
        );
        order = mock(Order.class);
        given(order.getId()).willReturn(10L);
        payment = mock(Payment.class);
        given(payment.getId()).willReturn(20L);
        given(payment.getOrder()).willReturn(order);
        given(payment.getAmount()).willReturn(30_000L);
        given(payment.isRefundableState()).willReturn(true);
        orderCancellation = mock(OrderCancellation.class);
        given(orderCancellation.getId()).willReturn(30L);
        given(orderCancellation.getOrder()).willReturn(order);
        given(orderCancellation.getStatus()).willReturn(OrderCancellationStatus.PROCESSING);
        given(orderCancellation.getReason()).willReturn("reason");
        given(orderCancellationRepository.findById(30L)).willReturn(Optional.of(orderCancellation));
        given(orderCancellationRepository.findByIdForUpdate(30L)).willReturn(Optional.of(orderCancellation));
        given(paymentRepository.findFirstByOrderIdOrderByIdDesc(10L)).willReturn(Optional.of(payment));
        given(paymentRepository.findByIdForUpdate(20L)).willReturn(Optional.of(payment));
        given(refundBalanceService.getBalance(payment))
                .willReturn(new PaymentRefundBalance(30_000L, 0L, 0L, 30_000L));
        given(paymentCancellationRepository.saveAndFlush(any(PaymentCancellation.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsPartialRecordWithBackendGeneratedStableKeys() {
        PaymentCancellation result = service.prepare(30L, 10_000L);

        assertThat(result.getType()).isEqualTo(PaymentCancellationType.PARTIAL);
        assertThat(result.getOrderCancellation()).isSameAs(orderCancellation);
        assertThat(result.getAmount()).isEqualTo(10_000L);
        assertThat(result.getClientRequestKey()).startsWith("PARTIAL-");
        assertThat(result.getIdempotencyKey()).isNotBlank();
        verify(refundBalanceService).validateRefundAmount(any(PaymentRefundBalance.class), eq(10_000L));
    }

    @Test
    void retryReturnsSameRecordAndIdempotencyKey() {
        PaymentCancellation existing = mock(PaymentCancellation.class);
        given(existing.getPayment()).willReturn(payment);
        given(existing.getAmount()).willReturn(10_000L);
        given(existing.getIdempotencyKey()).willReturn("same-key");
        given(paymentCancellationRepository.findByOrderCancellationId(30L))
                .willReturn(Optional.of(existing));

        PaymentCancellation result = service.prepare(30L, 10_000L);

        assertThat(result).isSameAs(existing);
        assertThat(result.getIdempotencyKey()).isEqualTo("same-key");
        verify(paymentCancellationRepository, never()).saveAndFlush(any());
    }

    @Test
    void retryWithDifferentAmountIsRejected() {
        PaymentCancellation existing = mock(PaymentCancellation.class);
        given(existing.getPayment()).willReturn(payment);
        given(existing.getAmount()).willReturn(10_000L);
        given(paymentCancellationRepository.findByOrderCancellationId(30L))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.prepare(30L, 9_000L))
                .isInstanceOf(RuntimeException.class);
    }
}
