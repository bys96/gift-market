package com.giftmarket.payment.service;

import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.dto.response.ExchangeShippingPaymentResponse;
import com.giftmarket.payment.entity.ExchangeShippingPaymentStatus;
import com.giftmarket.payment.entity.PaymentMethod;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.GatewayConfirmCommand;
import com.giftmarket.payment.gateway.GatewayConfirmResult;
import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.gateway.PaymentGateway;
import com.giftmarket.payment.gateway.PaymentGatewayRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExchangeShippingPaymentServiceTest {

    @Mock ExchangeShippingPaymentTransactionService transactionService;
    @Mock PaymentGatewayRegistry gatewayRegistry;
    @Mock PaymentGateway gateway;
    @InjectMocks ExchangeShippingPaymentService service;

    @Test
    void confirmCallsGatewayOnceWithPersistedPaymentData() {
        ExchangeShippingPaymentResponse succeeded = response(ExchangeShippingPaymentStatus.SUCCEEDED);
        GatewayConfirmResult result = success();
        given(transactionService.startConfirm(1L, 2L, request(6_000L)))
                .willReturn(start(ExchangeShippingPaymentStart.Action.CONFIRM, null));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.confirm(any())).willReturn(result);
        given(transactionService.apply(3L, result)).willReturn(succeeded);

        assertThat(service.confirm(1L, 2L, request(6_000L))).isSameAs(succeeded);

        ArgumentCaptor<GatewayConfirmCommand> captor = ArgumentCaptor.forClass(GatewayConfirmCommand.class);
        verify(gateway).confirm(captor.capture());
        assertThat(captor.getValue().providerPaymentKey()).isEqualTo("payment-key");
        assertThat(captor.getValue().merchantPaymentId()).isEqualTo("EXCHANGE-SHIPPING-2-1");
        assertThat(captor.getValue().amount()).isEqualTo(6_000L);
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("EXCHANGE-SHIPPING-PAYMENT-2-1");
        verify(transactionService).apply(3L, result);
    }

    @Test
    void alreadySucceededConfirmDoesNotCallGatewayAgain() {
        ExchangeShippingPaymentResponse succeeded = response(ExchangeShippingPaymentStatus.SUCCEEDED);
        given(transactionService.startConfirm(1L, 2L, request(6_000L)))
                .willReturn(start(ExchangeShippingPaymentStart.Action.COMPLETED, succeeded));

        assertThat(service.confirm(1L, 2L, request(6_000L))).isSameAs(succeeded);

        verify(gatewayRegistry, never()).get(any());
        verify(transactionService, never()).apply(any(), any(GatewayConfirmResult.class));
        verify(transactionService, never()).fail(any(), any(), any(), any());
    }

    @Test
    void definitiveGatewayFailureMarksPaymentFailedWithoutApplyingSuccess() {
        GatewayConfirmResult failed = new GatewayConfirmResult(
                GatewayPaymentStatus.FAILED, "payment-key", null,
                "EXCHANGE-SHIPPING-2-1", 6_000L, "KRW", null,
                null, "ABORTED", null
        );
        given(transactionService.startConfirm(1L, 2L, request(6_000L)))
                .willReturn(start(ExchangeShippingPaymentStart.Action.CONFIRM, null));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.confirm(any())).willReturn(failed);

        assertThatThrownBy(() -> service.confirm(1L, 2L, request(6_000L)))
                .isInstanceOf(PaymentException.class);

        verify(transactionService).fail(eq(3L), eq("PAYMENT_DECLINED"), anyString(), eq("ABORTED"));
        verify(transactionService, never()).apply(any(), any(GatewayConfirmResult.class));
    }

    private PaymentConfirmRequest request(long amount) {
        return new PaymentConfirmRequest("payment-key", "EXCHANGE-SHIPPING-2-1", amount);
    }

    private ExchangeShippingPaymentStart start(
            ExchangeShippingPaymentStart.Action action,
            ExchangeShippingPaymentResponse response
    ) {
        return new ExchangeShippingPaymentStart(
                action, 3L, PaymentProvider.TOSS, "payment-key",
                "EXCHANGE-SHIPPING-2-1", 6_000L,
                "EXCHANGE-SHIPPING-PAYMENT-2-1", response
        );
    }

    private GatewayConfirmResult success() {
        return new GatewayConfirmResult(
                GatewayPaymentStatus.PAID, "payment-key", "transaction-key",
                "EXCHANGE-SHIPPING-2-1", 6_000L, "KRW", PaymentMethod.CARD,
                null, "DONE", LocalDateTime.now()
        );
    }

    private ExchangeShippingPaymentResponse response(ExchangeShippingPaymentStatus status) {
        return new ExchangeShippingPaymentResponse(
                3L, 2L, status, 6_000L, "EXCHANGE-SHIPPING-2-1",
                "EXCHANGE-SHIPPING-PAYMENT-2-1", LocalDateTime.now().plusHours(1), "message"
        );
    }
}
