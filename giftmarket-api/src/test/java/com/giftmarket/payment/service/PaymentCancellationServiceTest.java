package com.giftmarket.payment.service;

import com.giftmarket.order.dto.request.OrderCancelRequest;
import com.giftmarket.order.dto.response.OrderCancelResponse;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentCancellationServiceTest {
    private final PaymentCancellationTransactionService transactions = mock(PaymentCancellationTransactionService.class);
    private final PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final PaymentCancellationService service = new PaymentCancellationService(transactions, registry);
    private final OrderCancelRequest request = new OrderCancelRequest("cancel-key", "고객 요청");

    @Test
    void fullCancellationCompletesOnlyAfterVerifiedGatewayResult() {
        PaymentCancelStart start = start(PaymentCancelStart.Action.CANCEL);
        GatewayCancelResult result = canceledResult();
        OrderCancelResponse response = new OrderCancelResponse(10L, OrderStatus.CANCELLED, PaymentStatus.CANCELED, "완료");
        when(transactions.start(1L, 10L, request)).thenReturn(start);
        when(registry.get(PaymentProvider.TOSS)).thenReturn(gateway);
        when(gateway.cancel(any())).thenReturn(result);
        when(transactions.complete(1L, 20L, 30L, result)).thenReturn(response);

        assertSame(response, service.cancel(1L, 10L, request));
        verify(gateway).cancel(argThat(command -> command.idempotencyKey().equals("cancel-idempotency")));
    }

    @Test
    void completedRetryDoesNotCallGateway() {
        OrderCancelResponse response = new OrderCancelResponse(10L, OrderStatus.CANCELLED, PaymentStatus.CANCELED, "이미 완료");
        when(transactions.start(1L, 10L, request)).thenReturn(new PaymentCancelStart(
                PaymentCancelStart.Action.COMPLETED, 20L, 30L, PaymentProvider.TOSS,
                null, null, null, null, null, null, response));
        assertSame(response, service.cancel(1L, 10L, request));
        verifyNoInteractions(registry, gateway);
    }

    @Test
    void uncertainCancellationKeepsTransactionPending() {
        when(transactions.start(1L, 10L, request)).thenReturn(start(PaymentCancelStart.Action.CANCEL));
        when(registry.get(PaymentProvider.TOSS)).thenReturn(gateway);
        when(gateway.cancel(any())).thenThrow(new PaymentGatewayUncertainException("timeout", null));
        assertThrows(PaymentException.class, () -> service.cancel(1L, 10L, request));
        verify(transactions, never()).explicitFailure(anyLong(), anyLong(), anyLong(), any(), any());
        verify(transactions, never()).complete(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void explicitDeclineReturnsPaymentToRetryableState() {
        when(transactions.start(1L, 10L, request)).thenReturn(start(PaymentCancelStart.Action.CANCEL));
        when(registry.get(PaymentProvider.TOSS)).thenReturn(gateway);
        when(gateway.cancel(any())).thenThrow(new PaymentGatewayDeclinedException("NOT_CANCELABLE", "declined"));
        assertThrows(PaymentException.class, () -> service.cancel(1L, 10L, request));
        verify(transactions).explicitFailure(1L, 20L, 30L, "NOT_CANCELABLE", "declined");
    }

    @Test
    void unknownResultRetryQueriesThenReusesSameCancelIdempotencyKey() {
        PaymentCancelStart start = start(PaymentCancelStart.Action.QUERY);
        GatewayPaymentQueryResult paid = new GatewayPaymentQueryResult(
                GatewayPaymentStatus.PAID, "payment-key", "tx", "merchant-id", 1000L,
                "KRW", PaymentMethod.CARD, null, "DONE", LocalDateTime.now());
        when(transactions.start(1L, 10L, request)).thenReturn(start);
        when(registry.get(PaymentProvider.TOSS)).thenReturn(gateway);
        when(gateway.getPayment("payment-key")).thenReturn(paid);
        when(gateway.cancel(any())).thenReturn(canceledResult());
        when(transactions.complete(eq(1L), eq(20L), eq(30L), any())).thenReturn(
                new OrderCancelResponse(10L, OrderStatus.CANCELLED, PaymentStatus.CANCELED, "완료"));
        service.cancel(1L, 10L, request);
        verify(gateway).cancel(argThat(command -> command.idempotencyKey().equals("cancel-idempotency")));
    }

    private PaymentCancelStart start(PaymentCancelStart.Action action) {
        return new PaymentCancelStart(action, 20L, 30L, PaymentProvider.TOSS, "payment-key",
                "merchant-id", 1000L, "KRW", "고객 요청", "cancel-idempotency", null);
    }
    private GatewayCancelResult canceledResult() {
        return new GatewayCancelResult(GatewayPaymentStatus.CANCELED, "payment-key", "cancel-tx",
                "merchant-id", 1000L, 0L, "KRW", "CANCELED", LocalDateTime.now());
    }
}
