package com.giftmarket.payment.service;

import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.dto.response.PaymentResponse;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.entity.PaymentMethod;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.GatewayConfirmCommand;
import com.giftmarket.payment.gateway.GatewayConfirmResult;
import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.gateway.GatewayPaymentQueryResult;
import com.giftmarket.payment.gateway.PaymentGateway;
import com.giftmarket.payment.gateway.PaymentGatewayDeclinedException;
import com.giftmarket.payment.gateway.PaymentGatewayRegistry;
import com.giftmarket.payment.gateway.PaymentGatewayUncertainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentTransactionService transactionService;
    @Mock PaymentGatewayRegistry gatewayRegistry;
    @Mock PaymentGateway gateway;
    @InjectMocks PaymentService service;

    @Test
    void alreadyPaidDoesNotCallGateway() {
        PaymentResponse paid = response(PaymentStatus.PAID);
        given(transactionService.startConfirm(any(), any(), any()))
                .willReturn(start(PaymentConfirmStart.Action.COMPLETED, paid));

        assertThat(service.confirm(1L, 2L, request())).isSameAs(paid);
        verify(gatewayRegistry, never()).get(any());
    }

    @Test
    void timeoutKeepsConfirming() {
        PaymentResponse confirming = response(PaymentStatus.CONFIRMING);
        given(transactionService.startConfirm(any(), any(), any()))
                .willReturn(start(PaymentConfirmStart.Action.CONFIRM, null));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.confirm(any())).willThrow(
                new PaymentGatewayUncertainException("확인 중", null)
        );
        given(transactionService.getPayment(1L, 2L)).willReturn(confirming);

        assertThat(service.confirm(1L, 2L, request()).status())
                .isEqualTo(PaymentStatus.CONFIRMING);
        verify(transactionService, never()).fail(any(), any(), any(), any(), any());
    }

    @Test
    void definitiveDeclineMarksFailed() {
        given(transactionService.startConfirm(any(), any(), any()))
                .willReturn(start(PaymentConfirmStart.Action.CONFIRM, null));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.confirm(any())).willThrow(
                new PaymentGatewayDeclinedException("DECLINED", "거절")
        );

        assertThatThrownBy(() -> service.confirm(1L, 2L, request()))
                .isInstanceOf(PaymentException.class);
        verify(transactionService).fail(1L, 2L, "DECLINED", "거절", null);
    }

    @Test
    void confirmingRetryQueriesInsteadOfConfirmingAgain() {
        PaymentResponse confirming = response(PaymentStatus.CONFIRMING);
        given(transactionService.startConfirm(any(), any(), any()))
                .willReturn(start(PaymentConfirmStart.Action.QUERY, null));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(
                new com.giftmarket.payment.gateway.GatewayPaymentQueryResult(
                        com.giftmarket.payment.gateway.GatewayPaymentStatus.PENDING,
                        "provider-key", null, "GM-PAY", 10_000L, "KRW",
                        null, null, "IN_PROGRESS", null
                )
        );
        given(transactionService.complete(
                any(),
                any(),
                any(GatewayPaymentQueryResult.class)
        )).willReturn(confirming);

        service.confirm(1L, 2L, request());

        verify(gateway, never()).confirm(any());
        verify(gateway).getPayment("provider-key");
    }

    @Test
    void confirmUsesPersistedIdempotencyKey() {
        PaymentResponse paid = response(PaymentStatus.PAID);
        LocalDateTime approvedAt = LocalDateTime.now();
        given(transactionService.startConfirm(any(), any(), any()))
                .willReturn(start(PaymentConfirmStart.Action.CONFIRM, null));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.confirm(any())).willReturn(new GatewayConfirmResult(
                GatewayPaymentStatus.PAID, "provider-key", "transaction-key",
                "GM-PAY", 10_000L, "KRW", PaymentMethod.CARD, null,
                "DONE", approvedAt
        ));
        given(transactionService.complete(any(), any(),
                any(GatewayConfirmResult.class))).willReturn(paid);

        service.confirm(1L, 2L, request());

        ArgumentCaptor<GatewayConfirmCommand> captor =
                ArgumentCaptor.forClass(GatewayConfirmCommand.class);
        verify(gateway).confirm(captor.capture());
        assertThat(captor.getValue().idempotencyKey())
                .isEqualTo("same-confirm-key");
    }

    @Test
    void statusPollingQueriesGatewayAndCompletesConfirmingPayment() {
        PaymentResponse paid = response(PaymentStatus.PAID);
        GatewayPaymentQueryResult queryResult =
                new GatewayPaymentQueryResult(
                        GatewayPaymentStatus.PAID,
                        "provider-key",
                        "transaction-key",
                        "GM-PAY",
                        10_000L,
                        "KRW",
                        PaymentMethod.CARD,
                        null,
                        "DONE",
                        LocalDateTime.now()
                );
        given(transactionService.startQuery(1L, 2L))
                .willReturn(start(PaymentConfirmStart.Action.QUERY, null));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(queryResult);
        given(transactionService.complete(1L, 2L, queryResult))
                .willReturn(paid);

        assertThat(service.getPayment(1L, 2L).status())
                .isEqualTo(PaymentStatus.PAID);
        verify(gateway).getPayment("provider-key");
        verify(transactionService).complete(1L, 2L, queryResult);
    }

    @Test
    void statusPollingDoesNotCallGatewayWhenPaymentIsNotConfirming() {
        PaymentResponse ready = response(PaymentStatus.READY);
        given(transactionService.startQuery(1L, 2L))
                .willReturn(start(PaymentConfirmStart.Action.COMPLETED, ready));

        assertThat(service.getPayment(1L, 2L)).isSameAs(ready);
        verify(gatewayRegistry, never()).get(any());
    }

    private PaymentConfirmRequest request() {
        return new PaymentConfirmRequest("provider-key", "GM-PAY", 10_000L);
    }

    private PaymentConfirmStart start(
            PaymentConfirmStart.Action action,
            PaymentResponse response
    ) {
        return new PaymentConfirmStart(
                action, PaymentProvider.TOSS, "provider-key", "GM-PAY",
                10_000L, "KRW", "same-confirm-key", null, response
        );
    }

    private PaymentResponse response(PaymentStatus status) {
        return new PaymentResponse(
                2L, 3L, status, 10_000L, null, null,
                null, null, status == PaymentStatus.PAID
                        ? "결제가 완료되었습니다."
                        : "결제 결과를 확인 중입니다."
        );
    }
}
