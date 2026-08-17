package com.giftmarket.payment.service;

import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.exception.PaymentWebhookRetryableException;
import com.giftmarket.payment.gateway.GatewayPaymentQueryResult;
import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.gateway.PaymentGateway;
import com.giftmarket.payment.gateway.PaymentGatewayRegistry;
import com.giftmarket.payment.gateway.PaymentGatewayUncertainException;
import com.giftmarket.payment.infrastructure.toss.dto.TossPaymentWebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TossPaymentWebhookServiceTest {

    private static final String TRANSMISSION_ID = "transmission-id";
    private static final Long PAYMENT_ID = 1L;

    @Mock PaymentWebhookEventService eventService;
    @Mock PaymentTransactionService transactionService;
    @Mock PaymentGatewayRegistry gatewayRegistry;
    @Mock PaymentGateway gateway;
    @Mock PartialPaymentCancellationReconciliationService partialCancellationReconciliationService;

    private TossPaymentWebhookService service;

    @BeforeEach
    void setUp() {
        service = new TossPaymentWebhookService(
                eventService,
                transactionService,
                gatewayRegistry,
                org.mockito.Mockito.mock(PaymentCancellationTransactionService.class),
                partialCancellationReconciliationService
        );
    }

    @Test
    void paymentStatusChangedQueriesTossAndAppliesPaidResult() {
        GatewayPaymentQueryResult result = result(GatewayPaymentStatus.PAID);
        prepareProcess(queryStart(), result);

        service.process(TRANSMISSION_ID, request("DONE"));

        verify(gateway).getPayment("provider-key");
        verify(transactionService).reconcileWebhook(
                PAYMENT_ID,
                "provider-key",
                result
        );
        verify(eventService).processed(
                PaymentProvider.TOSS,
                TRANSMISSION_ID,
                PAYMENT_ID
        );
    }

    @Test
    void duplicateProcessedEventReturnsWithoutSecondQuery() {
        GatewayPaymentQueryResult result = result(GatewayPaymentStatus.PAID);
        given(eventService.begin(
                PaymentProvider.TOSS,
                TRANSMISSION_ID,
                "PAYMENT_STATUS_CHANGED"
        )).willReturn(
                PaymentWebhookEventService.BeginResult.PROCESS,
                PaymentWebhookEventService.BeginResult.DUPLICATE
        );
        given(eventService.findTarget(
                PaymentProvider.TOSS,
                "GM-PAY",
                "provider-key"
        )).willReturn(found());
        given(transactionService.startWebhookQuery(
                PAYMENT_ID,
                PaymentProvider.TOSS,
                "provider-key",
                "GM-PAY"
        )).willReturn(queryStart());
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(result);

        service.process(TRANSMISSION_ID, request("DONE"));
        service.process(TRANSMISSION_ID, request("DONE"));

        verify(gateway, times(1)).getPayment("provider-key");
        verify(transactionService, times(1)).reconcileWebhook(
                PAYMENT_ID,
                "provider-key",
                result
        );
    }

    @Test
    void alreadyFinishedPaymentIsSuccessfulNoOp() {
        given(eventService.begin(
                PaymentProvider.TOSS,
                TRANSMISSION_ID,
                "PAYMENT_STATUS_CHANGED"
        )).willReturn(PaymentWebhookEventService.BeginResult.PROCESS);
        given(eventService.findTarget(
                PaymentProvider.TOSS,
                "GM-PAY",
                "provider-key"
        )).willReturn(found());
        given(transactionService.startWebhookQuery(
                PAYMENT_ID,
                PaymentProvider.TOSS,
                "provider-key",
                "GM-PAY"
        )).willReturn(completedStart());

        service.process(TRANSMISSION_ID, request("DONE"));

        verify(gatewayRegistry, never()).get(PaymentProvider.TOSS);
        verify(eventService).processed(
                PaymentProvider.TOSS,
                TRANSMISSION_ID,
                PAYMENT_ID
        );
    }

    @Test
    void pendingResultUsesSameReconciliationWithoutForcingState() {
        GatewayPaymentQueryResult result = result(GatewayPaymentStatus.PENDING);
        prepareProcess(queryStart(), result);

        service.process(TRANSMISSION_ID, request("IN_PROGRESS"));

        verify(transactionService).reconcileWebhook(
                PAYMENT_ID,
                "provider-key",
                result
        );
    }

    @Test
    void uncertainTossQueryRequestsWebhookRetry() {
        given(eventService.begin(
                PaymentProvider.TOSS,
                TRANSMISSION_ID,
                "PAYMENT_STATUS_CHANGED"
        )).willReturn(PaymentWebhookEventService.BeginResult.PROCESS);
        given(eventService.findTarget(
                PaymentProvider.TOSS,
                "GM-PAY",
                "provider-key"
        )).willReturn(found());
        given(transactionService.startWebhookQuery(
                PAYMENT_ID,
                PaymentProvider.TOSS,
                "provider-key",
                "GM-PAY"
        )).willReturn(queryStart());
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willThrow(
                new PaymentGatewayUncertainException("확인 중", null)
        );

        assertThatThrownBy(() -> service.process(
                TRANSMISSION_ID,
                request("DONE")
        )).isInstanceOf(PaymentWebhookRetryableException.class);

        verify(eventService).retryableFailure(
                PaymentProvider.TOSS,
                TRANSMISSION_ID,
                "PROVIDER_QUERY_UNCERTAIN"
        );
        verify(transactionService, never()).reconcileWebhook(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void missingPaymentRequestsRetryButIdentifierMismatchIsAcceptedAndRejected() {
        given(eventService.begin(
                PaymentProvider.TOSS,
                TRANSMISSION_ID,
                "PAYMENT_STATUS_CHANGED"
        )).willReturn(PaymentWebhookEventService.BeginResult.PROCESS);
        given(eventService.findTarget(
                PaymentProvider.TOSS,
                "GM-PAY",
                "provider-key"
        )).willReturn(PaymentWebhookEventService.TargetResult.notFound());

        assertThatThrownBy(() -> service.process(
                TRANSMISSION_ID,
                request("DONE")
        )).isInstanceOf(PaymentWebhookRetryableException.class);

        String mismatchId = "mismatch-transmission";
        given(eventService.begin(
                PaymentProvider.TOSS,
                mismatchId,
                "PAYMENT_STATUS_CHANGED"
        )).willReturn(PaymentWebhookEventService.BeginResult.PROCESS);
        given(eventService.findTarget(
                PaymentProvider.TOSS,
                "GM-PAY",
                "provider-key"
        )).willReturn(PaymentWebhookEventService.TargetResult.mismatch(PAYMENT_ID));

        service.process(mismatchId, request("DONE"));

        verify(eventService).rejected(
                PaymentProvider.TOSS,
                mismatchId,
                PAYMENT_ID,
                "PAYMENT_IDENTIFIER_MISMATCH"
        );
        verify(gatewayRegistry, never()).get(PaymentProvider.TOSS);
    }

    private void prepareProcess(
            PaymentConfirmStart start,
            GatewayPaymentQueryResult result
    ) {
        given(eventService.begin(
                PaymentProvider.TOSS,
                TRANSMISSION_ID,
                "PAYMENT_STATUS_CHANGED"
        )).willReturn(PaymentWebhookEventService.BeginResult.PROCESS);
        given(eventService.findTarget(
                PaymentProvider.TOSS,
                "GM-PAY",
                "provider-key"
        )).willReturn(found());
        given(transactionService.startWebhookQuery(
                PAYMENT_ID,
                PaymentProvider.TOSS,
                "provider-key",
                "GM-PAY"
        )).willReturn(start);
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(result);
    }

    private PaymentWebhookEventService.TargetResult found() {
        return PaymentWebhookEventService.TargetResult.found(PAYMENT_ID);
    }

    private TossPaymentWebhookRequest request(String status) {
        return new TossPaymentWebhookRequest(
                "PAYMENT_STATUS_CHANGED",
                "2026-08-16T12:00:00+09:00",
                new TossPaymentWebhookRequest.PaymentData(
                        "provider-key",
                        "GM-PAY",
                        status
                )
        );
    }

    private PaymentConfirmStart queryStart() {
        return new PaymentConfirmStart(
                PaymentConfirmStart.Action.QUERY,
                PaymentProvider.TOSS,
                "provider-key",
                "GM-PAY",
                10_000L,
                "KRW",
                "confirm-key",
                LocalDateTime.now(),
                null
        );
    }

    private PaymentConfirmStart completedStart() {
        return new PaymentConfirmStart(
                PaymentConfirmStart.Action.COMPLETED,
                PaymentProvider.TOSS,
                "provider-key",
                "GM-PAY",
                10_000L,
                "KRW",
                "confirm-key",
                LocalDateTime.now(),
                null
        );
    }

    private GatewayPaymentQueryResult result(GatewayPaymentStatus status) {
        return new GatewayPaymentQueryResult(
                status,
                "provider-key",
                "transaction-key",
                "GM-PAY",
                10_000L,
                "KRW",
                null,
                null,
                status == GatewayPaymentStatus.PAID ? "DONE" : "IN_PROGRESS",
                status == GatewayPaymentStatus.PAID ? LocalDateTime.now() : null
        );
    }
}
