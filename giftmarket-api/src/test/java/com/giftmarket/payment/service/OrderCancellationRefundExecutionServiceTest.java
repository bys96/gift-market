package com.giftmarket.payment.service;

import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.gateway.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCancellationRefundExecutionServiceTest {
    @Mock PartialPaymentCancellationTransactionService transactions;
    @Mock PaymentGatewayRegistry registry;
    @Mock PaymentGateway gateway;
    private OrderCancellationRefundExecutionService service;

    @BeforeEach void setUp() { service = new OrderCancellationRefundExecutionService(transactions, registry); }

    @Test
    void executesPartialCancellationWithSnapshotAmountAndIdempotencyKey() {
        PartialCancellationStart start = start();
        given(transactions.start(1L)).willReturn(start);
        given(registry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("payment-key")).willReturn(query(true));
        GatewayCancelResult result = result();
        given(gateway.cancel(any())).willReturn(result);
        service.execute(1L);
        verify(gateway).cancel(argThat(command -> command.isPartialCancellation()
                && command.cancelAmount() == 3_000L && command.idempotencyKey().equals("same-key")));
        verify(transactions).complete(start, result);
    }

    @Test
    void unsupportedPaymentMethodFailsWithoutCancelCall() {
        PartialCancellationStart start = start();
        given(transactions.start(1L)).willReturn(start);
        given(registry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("payment-key")).willReturn(query(false));
        service.execute(1L);
        verify(gateway, never()).cancel(any());
        verify(transactions).fail(1L, 30L, "NOT_PARTIAL_CANCELABLE_PAYMENT",
                "부분취소를 지원하지 않는 결제수단입니다.");
    }

    @Test
    void explicitDeclineIsFailed() {
        PartialCancellationStart start = start();
        given(transactions.start(1L)).willReturn(start);
        given(registry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("payment-key")).willReturn(query(true));
        given(gateway.cancel(any())).willThrow(new PaymentGatewayDeclinedException("NOT_CANCELABLE_AMOUNT", "거절"));
        service.execute(1L);
        verify(transactions).fail(1L, 30L, "NOT_CANCELABLE_AMOUNT", "거절");
        verify(transactions, never()).complete(any(), any());
    }

    @Test
    void uncertainResultKeepsProcessingState() {
        given(transactions.start(1L)).willReturn(start());
        given(registry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("payment-key")).willThrow(new PaymentGatewayUncertainException("timeout", null));
        service.execute(1L);
        verify(transactions, never()).fail(any(), any(), any(), any());
        verify(transactions, never()).complete(any(), any());
    }

    private PartialCancellationStart start() {
        return new PartialCancellationStart(PartialCancellationStart.Action.EXECUTE, 1L, 20L, 30L,
                PaymentProvider.TOSS, "payment-key", "order-id", 10_000L, 3_000L,
                "KRW", "고객 요청", "same-key");
    }

    private GatewayPaymentQueryResult query(boolean partialCancelable) {
        return new GatewayPaymentQueryResult(GatewayPaymentStatus.PAID, "payment-key", null,
                "order-id", 10_000L, "KRW", null, null, "DONE",
                LocalDateTime.now(), 10_000L, null, partialCancelable);
    }

    private GatewayCancelResult result() {
        return new GatewayCancelResult(GatewayPaymentStatus.PARTIALLY_CANCELED, "payment-key",
                "cancel-tx", "order-id", 10_000L, 7_000L, "KRW",
                "PARTIAL_CANCELED", LocalDateTime.now(), 3_000L, "DONE");
    }
}
