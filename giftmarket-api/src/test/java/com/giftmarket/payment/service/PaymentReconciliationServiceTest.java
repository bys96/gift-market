package com.giftmarket.payment.service;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.dto.response.PaymentResponse;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.gateway.GatewayPaymentQueryResult;
import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.gateway.PaymentGateway;
import com.giftmarket.payment.gateway.PaymentGatewayRegistry;
import com.giftmarket.payment.gateway.PaymentGatewayUncertainException;
import com.giftmarket.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentTransactionService transactionService;
    @Mock PaymentGatewayRegistry gatewayRegistry;
    @Mock PaymentGateway gateway;

    private PaymentReconciliationService service;
    private PaymentProperties properties;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        properties = new PaymentProperties();
        properties.setReconciliationDelaySeconds(30);
        properties.setReconciliationBatchSize(100);
        service = new PaymentReconciliationService(
                paymentRepository,
                transactionService,
                gatewayRegistry,
                properties
        );
        now = LocalDateTime.of(2026, 8, 16, 12, 0);
    }

    @Test
    void longRunningConfirmingQueriesGatewayAndAppliesResult() {
        LocalDateTime confirmingAt = now.minusMinutes(1);
        given(transactionService.startReconciliation(
                1L,
                now.minusSeconds(30)
        )).willReturn(queryStart(confirmingAt));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        GatewayPaymentQueryResult result = result(GatewayPaymentStatus.PAID);
        given(gateway.getPayment("provider-key")).willReturn(result);

        service.reconcileOne(1L, now.minusSeconds(30), now);

        verify(transactionService).reconcile(1L, result);
    }

    @Test
    void timeoutKeepsDatabaseStateUntouched() {
        given(transactionService.startReconciliation(any(), any()))
                .willReturn(queryStart(now.minusMinutes(1)));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willThrow(
                new PaymentGatewayUncertainException("확인 중", null)
        );

        service.reconcileOne(1L, now.minusSeconds(30), now);

        verify(transactionService, never()).reconcile(any(), any());
    }

    @Test
    void schedulerUsesBatchCandidatesAndContinuesAfterOneFailure() {
        given(paymentRepository.findReconciliationCandidateIds(
                eq(PaymentStatus.CONFIRMING),
                eq(OrderStatus.PENDING_PAYMENT),
                any(),
                any(Pageable.class)
        )).willReturn(List.of(1L, 2L));
        given(transactionService.startReconciliation(eq(1L), any()))
                .willThrow(new IllegalStateException("first failed"));
        given(transactionService.startReconciliation(eq(2L), any()))
                .willReturn(completedStart());

        service.reconcilePayments();

        verify(transactionService).startReconciliation(eq(2L), any());
        verify(gatewayRegistry, never()).get(any());
    }

    @Test
    void noDelayedCandidateMeansNoGatewayQuery() {
        given(paymentRepository.findReconciliationCandidateIds(
                eq(PaymentStatus.CONFIRMING),
                eq(OrderStatus.PENDING_PAYMENT),
                any(),
                any(Pageable.class)
        )).willReturn(List.of());

        service.reconcilePayments();

        verify(transactionService, never()).startReconciliation(any(), any());
        verify(gatewayRegistry, never()).get(any());
    }

    private PaymentConfirmStart queryStart(LocalDateTime confirmingAt) {
        return new PaymentConfirmStart(
                PaymentConfirmStart.Action.QUERY,
                PaymentProvider.TOSS,
                "provider-key",
                "GM-PAY",
                10_000L,
                "KRW",
                "confirm-key",
                confirmingAt,
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
                now.minusMinutes(1),
                new PaymentResponse(
                        1L, 2L, PaymentStatus.PAID, 10_000L,
                        null, null, now, now.plusMinutes(1), "완료"
                )
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
                "DONE",
                now
        );
    }
}
