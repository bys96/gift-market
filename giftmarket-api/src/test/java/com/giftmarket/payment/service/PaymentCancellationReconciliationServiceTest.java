package com.giftmarket.payment.service;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCancellationReconciliationServiceTest {

    @Mock PaymentCancellationRepository cancellationRepository;
    @Mock PaymentCancellationTransactionService transactionService;
    @Mock PaymentGatewayRegistry gatewayRegistry;
    @Mock PaymentGateway gateway;

    private PaymentCancellationReconciliationService service;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties();
        properties.setCancelReconciliationDelaySeconds(30);
        properties.setCancelReconciliationBatchSize(100);
        service = new PaymentCancellationReconciliationService(
                cancellationRepository,
                transactionService,
                gatewayRegistry,
                properties
        );
        now = LocalDateTime.of(2026, 8, 17, 12, 0);
    }

    @Test
    void fullCanceledQueryUsesCommonCompletion() {
        PaymentCancellationReconciliationStart start = queryStart(now.minusMinutes(2));
        GatewayPaymentQueryResult result = queryResult(
                GatewayPaymentStatus.CANCELED,
                "CANCELED",
                0L
        );
        given(transactionService.startReconciliation(1L, now.minusSeconds(30)))
                .willReturn(start);
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(result);

        service.reconcileOne(1L, now.minusSeconds(30), now);

        verify(transactionService).completeFromReconciliationQuery(1L, 2L, result);
        verify(gateway, never()).cancel(any());
    }

    @Test
    void paidQueryRetriesWithOriginalIdempotencyKey() {
        PaymentCancellationReconciliationStart start = queryStart(now.minusMinutes(2));
        GatewayPaymentQueryResult paid = queryResult(GatewayPaymentStatus.PAID, "DONE", 10_000L);
        GatewayCancelResult canceled = cancelResult();
        given(transactionService.startReconciliation(anyLong(), any()))
                .willReturn(start);
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(paid);
        given(gateway.cancel(any())).willReturn(canceled);

        service.reconcileOne(1L, now.minusSeconds(30), now);

        verify(gateway).cancel(argThat(command ->
                command.idempotencyKey().equals("original-cancel-key")
                        && command.reason().equals("고객 요청")
        ));
        verify(transactionService).completeFromReconciliationCancel(1L, 2L, canceled);
    }

    @Test
    void partialCanceledNeverCompletesOrRetries() {
        given(transactionService.startReconciliation(anyLong(), any()))
                .willReturn(queryStart(now.minusMinutes(2)));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(
                queryResult(GatewayPaymentStatus.UNKNOWN, "PARTIAL_CANCELED", 5_000L)
        );

        service.reconcileOne(1L, now.minusSeconds(30), now);

        verify(transactionService, never()).completeFromReconciliationQuery(anyLong(), anyLong(), any());
        verify(gateway, never()).cancel(any());
    }

    @Test
    void timeoutKeepsCancelingStateUntouched() {
        given(transactionService.startReconciliation(anyLong(), any()))
                .willReturn(queryStart(now.minusMinutes(2)));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willThrow(
                new PaymentGatewayUncertainException("timeout", null)
        );

        service.reconcileOne(1L, now.minusSeconds(30), now);

        verify(transactionService, never()).completeFromReconciliationQuery(anyLong(), anyLong(), any());
        verify(transactionService, never()).explicitReconciliationFailure(anyLong(), anyLong(), any(), any());
    }

    @Test
    void explicitCancelDeclineUsesExistingFailureTransition() {
        given(transactionService.startReconciliation(anyLong(), any()))
                .willReturn(queryStart(now.minusMinutes(2)));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(
                queryResult(GatewayPaymentStatus.PAID, "DONE", 10_000L)
        );
        given(gateway.cancel(any())).willThrow(
                new PaymentGatewayDeclinedException("NOT_CANCELABLE", "declined")
        );

        service.reconcileOne(1L, now.minusSeconds(30), now);

        verify(transactionService).explicitReconciliationFailure(
                1L, 2L, "NOT_CANCELABLE", "declined"
        );
    }

    @Test
    void expiredProviderIdempotencyDoesNotIssueNewCancel() {
        given(transactionService.startReconciliation(anyLong(), any()))
                .willReturn(queryStart(now.minusDays(15)));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(
                queryResult(GatewayPaymentStatus.PAID, "DONE", 10_000L)
        );

        service.reconcileOne(1L, now.minusSeconds(30), now);

        verify(gateway, never()).cancel(any());
    }

    @Test
    void schedulerUsesDelayedLimitedCandidatesAndContinuesAfterFailure() {
        given(cancellationRepository.findCancelReconciliationCandidatePaymentIds(
                eq(PaymentCancellationStatus.REQUESTED),
                eq(PaymentStatus.CANCELING),
                eq(OrderStatus.PAID),
                any(),
                any(Pageable.class)
        )).willReturn(List.of(1L, 2L));
        given(transactionService.startReconciliation(eq(1L), any()))
                .willThrow(new IllegalStateException("first failed"));
        given(transactionService.startReconciliation(eq(2L), any()))
                .willReturn(completedStart());

        service.reconcileCancellations();

        verify(transactionService).startReconciliation(eq(2L), any());
        verifyNoInteractions(gatewayRegistry);
    }

    private PaymentCancellationReconciliationStart queryStart(LocalDateTime requestedAt) {
        return new PaymentCancellationReconciliationStart(
                PaymentCancellationReconciliationStart.Action.QUERY,
                1L, 2L, PaymentProvider.TOSS, "provider-key", "merchant-id",
                10_000L, "KRW", "고객 요청", "original-cancel-key", requestedAt
        );
    }

    private PaymentCancellationReconciliationStart completedStart() {
        return new PaymentCancellationReconciliationStart(
                PaymentCancellationReconciliationStart.Action.COMPLETED,
                2L, null, PaymentProvider.TOSS, null, null,
                null, null, null, null, null
        );
    }

    private GatewayPaymentQueryResult queryResult(
            GatewayPaymentStatus status,
            String providerStatus,
            Long remainingAmount
    ) {
        return new GatewayPaymentQueryResult(
                status, "provider-key", "transaction-key", "merchant-id",
                10_000L, "KRW", PaymentMethod.CARD, null, providerStatus,
                now.minusDays(1), remainingAmount,
                status == GatewayPaymentStatus.CANCELED ? now : null
        );
    }

    private GatewayCancelResult cancelResult() {
        return new GatewayCancelResult(
                GatewayPaymentStatus.CANCELED, "provider-key", "cancel-transaction",
                "merchant-id", 10_000L, 0L, "KRW", "CANCELED", now
        );
    }
}
