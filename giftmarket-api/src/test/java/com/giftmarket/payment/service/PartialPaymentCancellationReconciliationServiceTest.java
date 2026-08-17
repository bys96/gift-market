package com.giftmarket.payment.service;

import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartialPaymentCancellationReconciliationServiceTest {

    @Mock PaymentCancellationRepository repository;
    @Mock PartialCancellationReconciliationTransactionService reconciliationTransactions;
    @Mock PartialPaymentCancellationTransactionService cancellationTransactions;
    @Mock PaymentGatewayRegistry gatewayRegistry;
    @Mock PaymentGateway gateway;
    private PartialPaymentCancellationReconciliationService service;
    private LocalDateTime requestedAt;

    @BeforeEach
    void setUp() {
        service = new PartialPaymentCancellationReconciliationService(repository, reconciliationTransactions,
                cancellationTransactions, gatewayRegistry, new PaymentProperties());
        requestedAt = LocalDateTime.of(2026, 8, 17, 10, 0);
    }

    @Test
    void completesOnlyUniquelyMatchedDoneCancellation() {
        PartialCancellationReconciliationStart start = start(null);
        given(reconciliationTransactions.start(10L, requestedAt.plusMinutes(1))).willReturn(start);
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(query(List.of(
                new GatewayCancellationTransaction("tx-1", 3_000L, "reason", "DONE",
                        requestedAt.plusSeconds(2), 7_000L))));

        service.reconcileOne(10L, requestedAt.plusMinutes(1), requestedAt.plusMinutes(2));

        ArgumentCaptor<GatewayCancelResult> result = ArgumentCaptor.forClass(GatewayCancelResult.class);
        verify(cancellationTransactions).complete(any(), result.capture());
        assertThat(result.getValue().providerTransactionId()).isEqualTo("tx-1");
        verify(gateway, never()).cancel(any());
    }

    @Test
    void doesNotGuessWhenSameAmountReasonAndTimeMatchMultipleTransactions() {
        PartialCancellationReconciliationStart start = start(null);
        given(reconciliationTransactions.start(10L, requestedAt.plusMinutes(1))).willReturn(start);
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(query(List.of(
                transaction("tx-1"), transaction("tx-2"))));

        service.reconcileOne(10L, requestedAt.plusMinutes(1), requestedAt.plusMinutes(2));

        verify(cancellationTransactions, never()).complete(any(), any());
        verify(gateway, never()).cancel(any());
    }

    @Test
    void retriesWithStoredAmountReasonAndIdempotencyKey() {
        PartialCancellationReconciliationStart start = start(null);
        given(reconciliationTransactions.start(10L, requestedAt.plusMinutes(1))).willReturn(start);
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(query(List.of()));
        given(gateway.cancel(any())).willReturn(cancelResult());

        service.reconcileOne(10L, requestedAt.plusMinutes(1), requestedAt.plusMinutes(2));

        ArgumentCaptor<GatewayCancelCommand> command = ArgumentCaptor.forClass(GatewayCancelCommand.class);
        verify(gateway).cancel(command.capture());
        assertThat(command.getValue().cancelAmount()).isEqualTo(3_000L);
        assertThat(command.getValue().reason()).isEqualTo("reason");
        assertThat(command.getValue().idempotencyKey()).isEqualTo("same-key");
        verify(cancellationTransactions).complete(any(), any());
    }

    private PartialCancellationReconciliationStart start(String transactionKey) {
        return new PartialCancellationReconciliationStart(
                PartialCancellationReconciliationStart.Action.QUERY, 1L, 10L, 20L,
                PaymentProvider.TOSS, "provider-key", "merchant-id", 10_000L, 3_000L,
                7_000L, "KRW", "reason", "same-key", transactionKey, requestedAt);
    }

    private GatewayCancellationTransaction transaction(String key) {
        return new GatewayCancellationTransaction(key, 3_000L, "reason", "DONE",
                requestedAt.plusSeconds(2), 7_000L);
    }

    private GatewayPaymentQueryResult query(List<GatewayCancellationTransaction> cancellations) {
        return new GatewayPaymentQueryResult(GatewayPaymentStatus.PARTIALLY_CANCELED,
                "provider-key", "latest", "merchant-id", 10_000L, "KRW", null, null,
                "PARTIAL_CANCELED", null, 7_000L, requestedAt.plusSeconds(2), true, cancellations);
    }

    private GatewayCancelResult cancelResult() {
        return new GatewayCancelResult(GatewayPaymentStatus.PARTIALLY_CANCELED, "provider-key", "tx-1",
                "merchant-id", 10_000L, 7_000L, "KRW", "PARTIAL_CANCELED",
                requestedAt.plusSeconds(2), 3_000L, "DONE");
    }
}
