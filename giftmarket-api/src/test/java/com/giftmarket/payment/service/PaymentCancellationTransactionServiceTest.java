package com.giftmarket.payment.service;

import com.giftmarket.order.dto.request.OrderCancelRequest;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.service.OrderInventoryService;
import com.giftmarket.order.service.SellerOrderLifecycleService;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.*;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCancellationTransactionServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentCancellationRepository cancellationRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderInventoryService inventoryService;
    @Mock SellerOrderLifecycleService sellerOrderLifecycleService;
    @Mock User user;

    private PaymentCancellationTransactionService service;
    private Payment payment;
    private Order order;
    private PaymentCancellation cancellation;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        service = new PaymentCancellationTransactionService(
                paymentRepository,
                cancellationRepository,
                orderRepository,
                inventoryService,
                sellerOrderLifecycleService
        );
        now = LocalDateTime.of(2026, 8, 17, 12, 0);
        order = Order.createPendingPayment(
                "GM-ORDER", user, 10_000L, 0L,
                "받는 사람", "010-1234-5678", "12345", "서울", null
        );
        ReflectionTestUtils.setField(order, "id", 10L);
        order.markPaid(now.minusDays(1));

        payment = Payment.createReady(
                order, PaymentProvider.TOSS, "merchant-id", "client-key",
                "confirm-key", 10_000L, "KRW", now.minusDays(1), now.plusDays(1)
        );
        ReflectionTestUtils.setField(payment, "id", 20L);
        payment.startConfirm("provider-key", now.minusDays(1));
        payment.complete(
                "provider-key", "approval-transaction", PaymentMethod.CARD,
                null, "DONE", now.minusDays(1)
        );
        payment.startCancel();

        cancellation = PaymentCancellation.create(
                payment, "cancel-client-key", "cancel-idempotency-key",
                "고객 요청", now.minusMinutes(2)
        );
        ReflectionTestUtils.setField(cancellation, "id", 30L);

        lenient().when(paymentRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(payment));
    }

    @Test
    void delayedCancelingCreatesQueryStartWithStoredRequest() {
        given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));
        given(cancellationRepository.findFirstByPaymentIdAndStatusOrderByIdDesc(
                20L, PaymentCancellationStatus.REQUESTED
        )).willReturn(Optional.of(cancellation));
        given(cancellationRepository.findByIdForUpdate(30L))
                .willReturn(Optional.of(cancellation));

        PaymentCancellationReconciliationStart result = service.startReconciliation(
                20L, now.minusSeconds(30)
        );

        assertThat(result.action()).isEqualTo(
                PaymentCancellationReconciliationStart.Action.QUERY
        );
        assertThat(result.idempotencyKey()).isEqualTo("cancel-idempotency-key");
        assertThat(result.requestedAt()).isEqualTo(now.minusMinutes(2));
    }

    @Test
    void cancelBeforeDelayIsNotQueried() {
        ReflectionTestUtils.setField(cancellation, "requestedAt", now.minusSeconds(10));
        given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));
        given(cancellationRepository.findFirstByPaymentIdAndStatusOrderByIdDesc(
                20L, PaymentCancellationStatus.REQUESTED
        )).willReturn(Optional.of(cancellation));
        given(cancellationRepository.findByIdForUpdate(30L))
                .willReturn(Optional.of(cancellation));

        assertThat(service.startReconciliation(20L, now.minusSeconds(30)).action())
                .isEqualTo(PaymentCancellationReconciliationStart.Action.COMPLETED);
    }

    @Test
    void fullCancellationRestoresInventoryAndFinalizesExactlyOnce() {
        given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));
        given(cancellationRepository.findByIdForUpdate(30L))
                .willReturn(Optional.of(cancellation));
        GatewayPaymentQueryResult result = canceledQuery();

        service.completeFromReconciliationQuery(20L, 30L, result);
        service.completeFromReconciliationQuery(20L, 30L, result);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancellation.getStatus()).isEqualTo(PaymentCancellationStatus.SUCCEEDED);
        verify(inventoryService, times(1)).restore(10L);
        verify(sellerOrderLifecycleService, times(1)).cancel(10L);
    }

    @Test
    void explicitDeclineReturnsPaidAndDoesNotRestoreInventory() {
        given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));
        given(cancellationRepository.findByIdForUpdate(30L))
                .willReturn(Optional.of(cancellation));

        service.explicitReconciliationFailure(20L, 30L, "NOT_CANCELABLE", "declined");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(cancellation.getStatus()).isEqualTo(PaymentCancellationStatus.FAILED);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void cancelsPendingReadyOrderAndSellerOrdersWithoutCallingPg() {
        Order pendingOrder = Order.createPendingPayment(
                "GM-PENDING", user, 10_000L, 0L,
                "받는 사람", "010-1234-5678", "12345", "서울", null
        );
        ReflectionTestUtils.setField(pendingOrder, "id", 11L);
        Payment readyPayment = Payment.createReady(
                pendingOrder, PaymentProvider.TOSS, "pending-merchant",
                "pending-client", "pending-confirm", 10_000L, "KRW",
                now, now.plusMinutes(30)
        );
        ReflectionTestUtils.setField(readyPayment, "id", 21L);
        given(orderRepository.findByIdAndUserId(11L, 1L))
                .willReturn(Optional.of(pendingOrder));
        given(paymentRepository.findFirstByOrderIdAndOrderUserIdOrderByIdDesc(
                11L, 1L
        )).willReturn(Optional.of(readyPayment));
        given(paymentRepository.findByIdForUpdate(21L))
                .willReturn(Optional.of(readyPayment));
        given(orderRepository.findByIdAndUserIdForUpdate(11L, 1L))
                .willReturn(Optional.of(pendingOrder));

        service.start(
                1L,
                11L,
                new OrderCancelRequest("cancel-request", "고객 요청")
        );

        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(readyPayment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        verify(inventoryService).restore(11L);
        verify(sellerOrderLifecycleService).cancel(11L);
    }

    @Test
    void webhookWonRaceSoSchedulerCompletionIsNoOp() {
        given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));
        given(cancellationRepository.findFirstByPaymentIdAndStatusOrderByIdDesc(
                20L, PaymentCancellationStatus.REQUESTED
        )).willReturn(Optional.of(cancellation));
        given(cancellationRepository.findByIdForUpdate(30L))
                .willReturn(Optional.of(cancellation));
        GatewayPaymentQueryResult result = canceledQuery();

        service.completeFromWebhook(20L, result);
        service.completeFromReconciliationQuery(20L, 30L, result);

        verify(inventoryService, times(1)).restore(10L);
    }

    private GatewayPaymentQueryResult canceledQuery() {
        return new GatewayPaymentQueryResult(
                GatewayPaymentStatus.CANCELED, "provider-key", "cancel-transaction",
                "merchant-id", 10_000L, "KRW", PaymentMethod.CARD, null,
                "CANCELED", now.minusDays(1), 0L, now
        );
    }
}
