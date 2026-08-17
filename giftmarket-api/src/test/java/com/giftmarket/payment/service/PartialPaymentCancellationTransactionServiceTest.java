package com.giftmarket.payment.service;

import com.giftmarket.order.dto.response.CancellationRefundCalculation;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.order.service.OrderCancellationCompletionService;
import com.giftmarket.order.service.OrderCancellationRefundCalculator;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartialPaymentCancellationTransactionServiceTest {
    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderCancellationRepository orderCancellationRepository;
    @Mock PaymentCancellationRepository paymentCancellationRepository;
    @Mock OrderCancellationRefundCalculator refundCalculator;
    @Mock PartialPaymentCancellationPreparationService preparationService;
    @Mock OrderCancellationCompletionService completionService;
    @Mock Payment payment;
    @Mock Order order;
    @Mock SellerOrder sellerOrder;
    @Mock OrderCancellation cancellation;
    @Mock PaymentCancellation pgCancellation;
    private PartialPaymentCancellationTransactionService service;

    @BeforeEach
    void setUp() {
        service = new PartialPaymentCancellationTransactionService(paymentRepository, orderRepository,
                sellerOrderRepository, orderCancellationRepository, paymentCancellationRepository,
                refundCalculator, preparationService, completionService);
    }

    @Test
    void immediatePaidRequestStartsProcessingAndSnapshotsRefund() {
        stubLockedGraph();
        given(cancellation.isRequiresSellerApproval()).willReturn(false);
        given(cancellation.getStatus()).willReturn(OrderCancellationStatus.REQUESTED);
        given(sellerOrder.getStatus()).willReturn(SellerOrderStatus.PAID);
        given(refundCalculator.calculate(1L)).willReturn(
                new CancellationRefundCalculation(1L, 3_000L, 0L, 3_000L, false, List.of()));
        given(preparationService.prepare(1L, 3_000L)).willReturn(pgCancellation);
        given(pgCancellation.getId()).willReturn(30L);
        given(pgCancellation.getType()).willReturn(PaymentCancellationType.PARTIAL);
        given(pgCancellation.getAmount()).willReturn(3_000L);
        given(pgCancellation.getReason()).willReturn("고객 요청");
        given(pgCancellation.getIdempotencyKey()).willReturn("same-key");

        PartialCancellationStart result = service.start(1L);

        verify(cancellation).startProcessing(org.mockito.ArgumentMatchers.any());
        assertThat(result.action()).isEqualTo(PartialCancellationStart.Action.EXECUTE);
        assertThat(result.cancelAmount()).isEqualTo(3_000L);
        assertThat(result.idempotencyKey()).isEqualTo("same-key");
    }

    @Test
    void successfulPartialResultCompletesCommerceCancellationOnce() {
        stubLockedGraph();
        given(paymentCancellationRepository.findByIdForUpdate(30L)).willReturn(Optional.of(pgCancellation));
        given(pgCancellation.getStatus()).willReturn(PaymentCancellationStatus.REQUESTED);
        given(pgCancellation.getType()).willReturn(PaymentCancellationType.PARTIAL);
        given(pgCancellation.getOrderCancellation()).willReturn(cancellation);
        given(pgCancellation.getAmount()).willReturn(3_000L);
        given(cancellation.getStatus()).willReturn(OrderCancellationStatus.PROCESSING);
        PartialCancellationStart start = start();
        GatewayCancelResult result = new GatewayCancelResult(
                GatewayPaymentStatus.PARTIALLY_CANCELED, "payment-key", "cancel-tx", "order-id",
                10_000L, 7_000L, "KRW", "PARTIAL_CANCELED", LocalDateTime.now(), 3_000L, "DONE");

        service.complete(start, result);

        verify(completionService).complete(1L);
        verify(pgCancellation).succeed(org.mockito.ArgumentMatchers.eq("cancel-tx"),
                org.mockito.ArgumentMatchers.any());
        verify(payment).markPartiallyCanceled("PARTIAL_CANCELED");
    }

    @Test
    void finalPartialCancelWithProviderPartialStatusAndZeroBalancesCompletesOnce() {
        stubLockedGraph();
        given(paymentCancellationRepository.findByIdForUpdate(30L)).willReturn(Optional.of(pgCancellation));
        given(pgCancellation.getStatus()).willReturn(
                PaymentCancellationStatus.REQUESTED, PaymentCancellationStatus.REQUESTED,
                PaymentCancellationStatus.SUCCEEDED);
        given(pgCancellation.getType()).willReturn(PaymentCancellationType.PARTIAL);
        given(pgCancellation.getOrderCancellation()).willReturn(cancellation);
        given(pgCancellation.getAmount()).willReturn(3_000L);
        given(pgCancellation.getProviderTransactionKey()).willReturn("final-cancel-tx");
        given(cancellation.getStatus()).willReturn(
                OrderCancellationStatus.PROCESSING, OrderCancellationStatus.COMPLETED);
        given(sellerOrder.getStatus()).willReturn(SellerOrderStatus.CANCELLED);
        given(sellerOrderRepository.findAllByOrderIdOrderByIdAsc(10L)).willReturn(List.of(sellerOrder));
        GatewayCancelResult result = new GatewayCancelResult(
                GatewayPaymentStatus.PARTIALLY_CANCELED, "payment-key", "final-cancel-tx", "order-id",
                10_000L, 0L, "KRW", "PARTIAL_CANCELED", LocalDateTime.now(),
                3_000L, "DONE", 0L);

        service.complete(start(), result);
        service.complete(start(), result);

        verify(completionService, times(1)).complete(1L);
        verify(pgCancellation, times(1)).succeed(org.mockito.ArgumentMatchers.eq("final-cancel-tx"),
                org.mockito.ArgumentMatchers.any());
        verify(payment).markFullyCanceled(org.mockito.ArgumentMatchers.eq("PARTIAL_CANCELED"),
                org.mockito.ArgumentMatchers.any());
        verify(order).cancel();
    }

    @Test
    void finalCancelStillRejectsNonDoneOrMismatchedTransactionBalance() {
        stubLockedGraph();
        given(paymentCancellationRepository.findByIdForUpdate(30L)).willReturn(Optional.of(pgCancellation));
        given(pgCancellation.getStatus()).willReturn(PaymentCancellationStatus.REQUESTED);
        given(pgCancellation.getType()).willReturn(PaymentCancellationType.PARTIAL);
        given(pgCancellation.getOrderCancellation()).willReturn(cancellation);
        given(pgCancellation.getAmount()).willReturn(3_000L);
        given(cancellation.getStatus()).willReturn(OrderCancellationStatus.PROCESSING);

        assertThatThrownBy(() -> service.complete(start(), new GatewayCancelResult(
                GatewayPaymentStatus.PARTIALLY_CANCELED, "payment-key", "cancel-tx", "order-id",
                10_000L, 0L, "KRW", "PARTIAL_CANCELED", LocalDateTime.now(),
                3_000L, "IN_PROGRESS", 0L))).isInstanceOf(com.giftmarket.payment.exception.PaymentException.class);
        assertThatThrownBy(() -> service.complete(start(), new GatewayCancelResult(
                GatewayPaymentStatus.PARTIALLY_CANCELED, "payment-key", "cancel-tx", "order-id",
                10_000L, 0L, "KRW", "PARTIAL_CANCELED", LocalDateTime.now(),
                3_000L, "DONE", 1_000L))).isInstanceOf(com.giftmarket.payment.exception.PaymentException.class);
    }

    private void stubLockedGraph() {
        given(cancellation.getOrder()).willReturn(order);
        given(cancellation.getSellerOrder()).willReturn(sellerOrder);
        given(cancellation.getId()).willReturn(1L);
        given(order.getId()).willReturn(10L);
        given(sellerOrder.getId()).willReturn(11L);
        given(payment.getId()).willReturn(20L);
        given(payment.getOrder()).willReturn(order);
        given(payment.getProvider()).willReturn(PaymentProvider.TOSS);
        given(payment.getProviderPaymentKey()).willReturn("payment-key");
        given(payment.getMerchantPaymentId()).willReturn("order-id");
        given(payment.getAmount()).willReturn(10_000L);
        given(payment.getCurrency()).willReturn("KRW");
        given(payment.isRefundableState()).willReturn(true);
        given(order.getStatus()).willReturn(OrderStatus.PAID);
        given(paymentRepository.findFirstByOrderIdOrderByIdDesc(10L)).willReturn(Optional.of(payment));
        given(paymentRepository.findByIdForUpdate(20L)).willReturn(Optional.of(payment));
        given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));
        given(sellerOrderRepository.findByIdAndOrderIdForUpdate(11L, 10L)).willReturn(Optional.of(sellerOrder));
        given(orderCancellationRepository.findById(1L)).willReturn(Optional.of(cancellation));
        given(orderCancellationRepository.findByIdForUpdate(1L)).willReturn(Optional.of(cancellation));
    }

    private PartialCancellationStart start() {
        return new PartialCancellationStart(PartialCancellationStart.Action.EXECUTE, 1L, 20L, 30L,
                PaymentProvider.TOSS, "payment-key", "order-id", 10_000L, 3_000L,
                "KRW", "고객 요청", "same-key");
    }
}
