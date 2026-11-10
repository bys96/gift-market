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
                new GatewayCancellationTransaction("tx-1", 3_000L, PartialPaymentCancellationReason.create(20L), "DONE",
                        requestedAt.plusSeconds(2), 7_000L))));

        service.reconcileOne(10L, requestedAt.plusMinutes(1), requestedAt.plusMinutes(2));

        ArgumentCaptor<GatewayCancelResult> result = ArgumentCaptor.forClass(GatewayCancelResult.class);
        verify(cancellationTransactions).completeFromReconciliation(any(), result.capture());
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

        verifyNoInteractions(cancellationTransactions);
        verify(gateway, never()).cancel(any());
    }

    @Test
    void retriesWithStoredAmountReasonAndIdempotencyKey() {
        PartialCancellationReconciliationStart start = start(null);
        given(reconciliationTransactions.start(10L, requestedAt.plusMinutes(1))).willReturn(start);
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(new GatewayPaymentQueryResult(
                GatewayPaymentStatus.PAID, "provider-key", "latest", "merchant-id", 10_000L,
                "KRW", null, null, "DONE", null, 10_000L, null, true, List.of()));
        given(gateway.cancel(any())).willReturn(cancelResult());

        service.reconcileOne(10L, requestedAt.plusMinutes(1), requestedAt.plusMinutes(2));

        ArgumentCaptor<GatewayCancelCommand> command = ArgumentCaptor.forClass(GatewayCancelCommand.class);
        verify(gateway).cancel(command.capture());
        assertThat(command.getValue().cancelAmount()).isEqualTo(3_000L);
        assertThat(command.getValue().reason()).isEqualTo(PartialPaymentCancellationReason.create(20L));
        assertThat(command.getValue().idempotencyKey()).isEqualTo("same-key");
        verify(cancellationTransactions).complete(any(), any());
    }

    @Test
    void mismatchedBalanceDoesNotRetryUnidentifiedCancellation() {
        given(reconciliationTransactions.start(10L, requestedAt.plusMinutes(1))).willReturn(start(null));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(query(List.of()));
        service.reconcileOne(10L, requestedAt.plusMinutes(1), requestedAt.plusMinutes(2));
        verify(gateway, never()).cancel(any());
        verifyNoInteractions(cancellationTransactions);
    }

    @Test
    void completesFinalCancellationFromMatchedPartialCanceledZeroBalanceQuery() {
        PartialCancellationReconciliationStart start = new PartialCancellationReconciliationStart(
                PartialCancellationReconciliationStart.Action.QUERY, 1L, 10L, 20L,
                PaymentProvider.TOSS, "provider-key", "merchant-id", 2_000L, 1_000L,
                0L, "KRW", PartialPaymentCancellationReason.create(20L), "same-key", null, requestedAt);
        given(reconciliationTransactions.start(10L, requestedAt.plusMinutes(1))).willReturn(start);
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(new GatewayPaymentQueryResult(
                GatewayPaymentStatus.PARTIALLY_CANCELED, "provider-key", "tx-2", "merchant-id",
                2_000L, "KRW", null, null, "PARTIAL_CANCELED", null, 0L,
                requestedAt.plusSeconds(2), false, List.of(new GatewayCancellationTransaction(
                        "tx-2", 1_000L, PartialPaymentCancellationReason.create(20L), "DONE", requestedAt.plusSeconds(2), 0L),
                        new GatewayCancellationTransaction("prior", 1_000L, "other-request", "DONE", requestedAt.minusSeconds(1), 1_000L))));

        service.reconcileOne(10L, requestedAt.plusMinutes(1), requestedAt.plusMinutes(2));

        ArgumentCaptor<GatewayCancelResult> result = ArgumentCaptor.forClass(GatewayCancelResult.class);
        verify(cancellationTransactions).completeFromReconciliation(any(), result.capture());
        assertThat(result.getValue().remainingAmount()).isZero();
        assertThat(result.getValue().transactionRemainingAmount()).isZero();
        verify(gateway, never()).cancel(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void recoversEarlierRefundAfterLaterRefundRegardlessOfHistoryArrayOrder(boolean reverse) {
        var a = transaction("tx-a");
        var b = new GatewayCancellationTransaction("tx-b", 2_000L, "other-request", "DONE", requestedAt.plusSeconds(3), 5_000L);
        runQuery(start(null), history(5_000L, reverse ? List.of(b, a) : List.of(a, b)));
        ArgumentCaptor<GatewayCancelResult> result = ArgumentCaptor.forClass(GatewayCancelResult.class);
        verify(cancellationTransactions).completeFromReconciliation(any(), result.capture());
        assertThat(result.getValue().providerTransactionId()).isEqualTo("tx-a");
        assertThat(result.getValue().remainingAmount()).isEqualTo(5_000L);
        assertThat(result.getValue().transactionRemainingAmount()).isEqualTo(7_000L);
        verify(gateway, never()).cancel(any());
    }

    @Test
    void sameAmountDifferentRequestReasonsIdentifyOnlyTheRequestedRefund() {
        runQuery(start(null), history(4_000L, List.of(transaction("tx-a"),
                new GatewayCancellationTransaction("tx-b", 3_000L, "other-request", "DONE", requestedAt.plusSeconds(3), 4_000L))));
        var result = ArgumentCaptor.forClass(GatewayCancelResult.class);
        verify(cancellationTransactions).completeFromReconciliation(any(), result.capture());
        assertThat(result.getValue().providerTransactionId()).isEqualTo("tx-a");
        verify(gateway, never()).cancel(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void recoversTargetWhenItIsLastOrInTheMiddleOfSeveralRefunds(boolean middle) {
        var before = new GatewayCancellationTransaction("before", middle ? 1_000L : 2_000L,
                "other-before", "DONE", requestedAt.plusSeconds(1), middle ? 9_000L : 8_000L);
        var target = new GatewayCancellationTransaction("target", 3_000L, PartialPaymentCancellationReason.create(20L),
                "DONE", requestedAt.plusSeconds(2), middle ? 6_000L : 5_000L);
        var after = new GatewayCancellationTransaction("after", 1_000L, "other-after", "DONE", requestedAt.plusSeconds(3), 5_000L);
        runQuery(start(null), history(5_000L, middle ? List.of(after, target, before) : List.of(target, before)));
        var result = ArgumentCaptor.forClass(GatewayCancelResult.class);
        verify(cancellationTransactions).completeFromReconciliation(any(), result.capture());
        assertThat(result.getValue().providerTransactionId()).isEqualTo("target");
        verify(gateway, never()).cancel(any());
    }

    @Test
    void knownProviderKeyIdentifiesHistoricalRefundWithoutGenericReasonGuessing() {
        runQuery(start("tx-a"), history(5_000L, List.of(
                transaction("tx-a"), new GatewayCancellationTransaction("tx-b", 2_000L, "other", "DONE", requestedAt.plusSeconds(3), 5_000L))));
        verify(cancellationTransactions).completeFromReconciliation(any(), any());
        verify(gateway, never()).cancel(any());
    }

    @Test
    void missingKnownTransactionDoesNotRetryOrComplete() {
        runQuery(start("missing"), query(List.of(transaction("tx-a"))));
        verifyNoInteractions(cancellationTransactions);
        verify(gateway, never()).cancel(any());
    }

    @Test
    void missingTargetInHistoryWithOtherRefundsRemainsUnresolved() {
        runQuery(start(null), history(8_000L, List.of(new GatewayCancellationTransaction(
                "other", 2_000L, "other-request", "DONE", requestedAt.plusSeconds(2), 8_000L))));
        verifyNoInteractions(cancellationTransactions);
        verify(gateway, never()).cancel(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"generic", "missing-key", "pending", "duplicate-key", "incomplete", "wrong-amount"})
    void uncertainOrInconsistentHistoryIsNeverCompletedOrRetried(String kind) {
        var a = transaction("tx-a");
        List<GatewayCancellationTransaction> transactions = switch (kind) {
            case "generic" -> List.of(new GatewayCancellationTransaction("tx-a", 3_000L, "고객 요청", "DONE", requestedAt.plusSeconds(2), 7_000L));
            case "missing-key" -> List.of(new GatewayCancellationTransaction(null, 3_000L, a.reason(), "DONE", a.canceledAt(), 7_000L));
            case "pending" -> List.of(new GatewayCancellationTransaction("tx-a", 3_000L, a.reason(), "PENDING", a.canceledAt(), 7_000L));
            case "duplicate-key" -> List.of(a, new GatewayCancellationTransaction("tx-a", 2_000L, "other", "DONE", a.canceledAt(), 5_000L));
            case "incomplete" -> List.of(a);
            default -> List.of(new GatewayCancellationTransaction("tx-a", 2_000L, a.reason(), "DONE", a.canceledAt(), 8_000L));
        };
        long remaining = kind.equals("duplicate-key") || kind.equals("incomplete") ? 5_000L : kind.equals("wrong-amount") ? 8_000L : 7_000L;
        runQuery(start(null), history(remaining, transactions));
        verifyNoInteractions(cancellationTransactions);
        verify(gateway, never()).cancel(any());
    }

    @Test
    void queryTimeoutDoesNotIssueRefundOrChangeLocalState() {
        given(reconciliationTransactions.start(10L, requestedAt.plusMinutes(1))).willReturn(start(null));
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willThrow(new PaymentGatewayUncertainException("test timeout", null));
        service.reconcileOne(10L, requestedAt.plusMinutes(1), requestedAt.plusMinutes(2));
        verifyNoInteractions(cancellationTransactions);
        verify(gateway, never()).cancel(any());
    }

    private void runQuery(PartialCancellationReconciliationStart start, GatewayPaymentQueryResult query) {
        given(reconciliationTransactions.start(10L, requestedAt.plusMinutes(1))).willReturn(start);
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.getPayment("provider-key")).willReturn(query);
        service.reconcileOne(10L, requestedAt.plusMinutes(1), requestedAt.plusMinutes(2));
    }

    @Test
    void sameRequestMarkerOnTwoSuccessfulTransactionsRemainsAmbiguousEvenWithValidBalances() {
        runQuery(start(null), history(4_000L, List.of(transaction("tx-a"),
                new GatewayCancellationTransaction("tx-b", 3_000L, PartialPaymentCancellationReason.create(20L),
                        "DONE", requestedAt.plusSeconds(3), 4_000L))));
        verifyNoInteractions(cancellationTransactions);
        verify(gateway, never()).cancel(any());
    }

    @Test
    void legacyGenericReasonWithoutStoredTransactionKeyCannotIdentifyRequest() {
        var legacy = new PartialCancellationReconciliationStart(
                PartialCancellationReconciliationStart.Action.QUERY, 1L, 10L, 20L,
                PaymentProvider.TOSS, "provider-key", "merchant-id", 10_000L, 3_000L,
                7_000L, "KRW", "고객 요청", "same-key", null, requestedAt);
        runQuery(legacy, history(7_000L, List.of(new GatewayCancellationTransaction(
                "tx-a", 3_000L, "고객 요청", "DONE", requestedAt.plusSeconds(2), 7_000L))));
        verifyNoInteractions(cancellationTransactions);
        verify(gateway, never()).cancel(any());
    }

    private GatewayPaymentQueryResult history(long remaining, List<GatewayCancellationTransaction> transactions) {
        return new GatewayPaymentQueryResult(GatewayPaymentStatus.PARTIALLY_CANCELED,
                "provider-key", "latest", "merchant-id", 10_000L, "KRW", null, null,
                "PARTIAL_CANCELED", null, remaining, requestedAt.plusSeconds(3), true, transactions);
    }

    private PartialCancellationReconciliationStart start(String transactionKey) {
        return new PartialCancellationReconciliationStart(
                PartialCancellationReconciliationStart.Action.QUERY, 1L, 10L, 20L,
                PaymentProvider.TOSS, "provider-key", "merchant-id", 10_000L, 3_000L,
                7_000L, "KRW", PartialPaymentCancellationReason.create(20L), "same-key", transactionKey, requestedAt);
    }

    private GatewayCancellationTransaction transaction(String key) {
        return new GatewayCancellationTransaction(key, 3_000L, PartialPaymentCancellationReason.create(20L), "DONE",
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
