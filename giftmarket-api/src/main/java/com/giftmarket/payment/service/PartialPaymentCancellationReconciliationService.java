package com.giftmarket.payment.service;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.exception.PartialCancellationValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartialPaymentCancellationReconciliationService {

    private static final long TOSS_IDEMPOTENCY_VALID_DAYS = 15;

    private final PaymentCancellationRepository cancellationRepository;
    private final PartialCancellationReconciliationTransactionService reconciliationTransactions;
    private final PartialPaymentCancellationTransactionService cancellationTransactions;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentProperties properties;

    @Scheduled(fixedDelayString = "#{@paymentProperties.partialCancellationReconciliationCheckIntervalMillis}")
    public void reconcilePartialCancellations() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime requestedBefore = now.minusSeconds(
                properties.getPartialCancellationReconciliationDelaySeconds());
        List<Long> ids = cancellationRepository.findPartialReconciliationCandidateIds(
                PaymentCancellationType.PARTIAL, PaymentCancellationStatus.REQUESTED,
                List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_CANCELED), OrderStatus.PAID,
                List.of(SellerOrderStatus.PAID, SellerOrderStatus.PREPARING), requestedBefore,
                PageRequest.of(0, properties.getPartialCancellationReconciliationBatchSize()));
        for (Long id : ids) {
            try {
                reconcileOne(id, requestedBefore, now);
            } catch (RuntimeException exception) {
                log.error("Partial cancellation reconciliation failed. paymentCancellationId={}, exceptionType={}, validationType={}",
                        id, exception.getClass().getSimpleName(), validationType(exception));
            }
        }
    }

    void reconcileOne(Long paymentCancellationId, LocalDateTime requestedBefore, LocalDateTime now) {
        PartialCancellationReconciliationStart start = reconciliationTransactions
                .start(paymentCancellationId, requestedBefore);
        if (start.action() != PartialCancellationReconciliationStart.Action.QUERY) return;

        long ageSeconds = Math.max(0, Duration.between(start.requestedAt(), now).getSeconds());
        log.warn("Long-running partial cancellation reconciliation started. paymentId={}, paymentCancellationId={}, orderCancellationId={}, provider={}, ageSeconds={}",
                start.paymentId(), start.paymentCancellationId(), start.orderCancellationId(), start.provider(), ageSeconds);
        PaymentGateway gateway = gatewayRegistry.get(start.provider());
        try {
            GatewayPaymentQueryResult query = gateway.getPayment(start.providerPaymentKey());
            ReconciliationResult result = reconcileQuery(start, query);
            if (result == ReconciliationResult.COMPLETED || result == ReconciliationResult.UNRESOLVED) {
                logResult(start, ageSeconds, result);
                return;
            }
            if (!start.requestedAt().plusDays(TOSS_IDEMPOTENCY_VALID_DAYS).isAfter(now)) {
                log.warn("Partial cancellation retry skipped because idempotency validity elapsed. paymentId={}, paymentCancellationId={}, orderCancellationId={}, provider={}, ageSeconds={}",
                        start.paymentId(), start.paymentCancellationId(), start.orderCancellationId(), start.provider(), ageSeconds);
                return;
            }
            GatewayCancelResult cancel = gateway.cancel(GatewayCancelCommand.partial(
                    start.providerPaymentKey(), start.merchantPaymentId(), start.originalAmount(),
                    start.currency(), start.reason(), start.idempotencyKey(), start.cancelAmount()));
            cancellationTransactions.complete(toCompletionStart(start), cancel);
            logResult(start, ageSeconds, ReconciliationResult.RETRY_COMPLETED);
        } catch (PaymentGatewayDeclinedException exception) {
            cancellationTransactions.fail(start.orderCancellationId(), start.paymentCancellationId(),
                    exception.getFailureCode(), exception.getMessage());
            log.warn("Partial cancellation reconciliation explicitly declined. paymentId={}, paymentCancellationId={}, orderCancellationId={}, provider={}, ageSeconds={}, resultType=DECLINED, exceptionType={}",
                    start.paymentId(), start.paymentCancellationId(), start.orderCancellationId(), start.provider(), ageSeconds,
                    exception.getClass().getSimpleName());
        } catch (PaymentGatewayUncertainException exception) {
            log.warn("Partial cancellation reconciliation remains uncertain. paymentId={}, paymentCancellationId={}, orderCancellationId={}, provider={}, ageSeconds={}, resultType=UNCERTAIN, exceptionType={}",
                    start.paymentId(), start.paymentCancellationId(), start.orderCancellationId(), start.provider(), ageSeconds,
                    exception.getClass().getSimpleName());
        }
    }

    public void reconcileFromWebhook(Long paymentId, GatewayPaymentQueryResult query) {
        for (Long id : cancellationRepository.findRequestedPartialIdsByPaymentId(paymentId)) {
            try {
                PartialCancellationReconciliationStart start = reconciliationTransactions.start(id, LocalDateTime.now());
                if (start.action() == PartialCancellationReconciliationStart.Action.QUERY) reconcileQuery(start, query);
            } catch (RuntimeException exception) {
                log.warn("Partial cancellation webhook reconciliation remains unresolved. paymentId={}, paymentCancellationId={}, exceptionType={}",
                        paymentId, id, exception.getClass().getSimpleName());
            }
        }
    }

    public boolean hasRequestedPartialCancellation(Long paymentId) {
        return !cancellationRepository.findRequestedPartialIdsByPaymentId(paymentId).isEmpty();
    }

    private ReconciliationResult reconcileQuery(PartialCancellationReconciliationStart start,
                                                 GatewayPaymentQueryResult query) {
        if (!validIdentity(start, query)) return ReconciliationResult.UNRESOLVED;
        Match match = match(start, query.cancellations());
        if (match.type == MatchType.CONFLICT || match.type == MatchType.AMBIGUOUS)
            return ReconciliationResult.UNRESOLVED;
        if (match.transaction != null) {
            if (!"DONE".equalsIgnoreCase(match.transaction.status())
                    || !Objects.equals(query.remainingAmount(), start.expectedRemainingAmount())
                    || !consistentStatus(query.status(), query.remainingAmount()))
                return ReconciliationResult.UNRESOLVED;
            GatewayCancellationTransaction tx = match.transaction;
            if (!Objects.equals(tx.remainingAmount(), query.remainingAmount())) {
                return ReconciliationResult.UNRESOLVED;
            }
            cancellationTransactions.complete(toCompletionStart(start), new GatewayCancelResult(
                    query.status(), query.providerPaymentKey(), tx.providerTransactionId(),
                    query.merchantPaymentId(), query.amount(), query.remainingAmount(), query.currency(),
                    query.providerStatus(), tx.canceledAt(), tx.amount(), tx.status(), tx.remainingAmount()));
            return ReconciliationResult.COMPLETED;
        }
        boolean retryableState = query.status() == GatewayPaymentStatus.PAID
                || query.status() == GatewayPaymentStatus.PARTIALLY_CANCELED;
        return retryableState && Boolean.TRUE.equals(query.partialCancelable())
                ? ReconciliationResult.RETRY_SAFE : ReconciliationResult.UNRESOLVED;
    }

    private Match match(PartialCancellationReconciliationStart start,
                        List<GatewayCancellationTransaction> transactions) {
        List<GatewayCancellationTransaction> values = transactions == null ? List.of() : transactions;
        if (start.providerTransactionKey() != null && !start.providerTransactionKey().isBlank()) {
            List<GatewayCancellationTransaction> exact = values.stream()
                    .filter(value -> start.providerTransactionKey().equals(value.providerTransactionId())).toList();
            if (exact.size() != 1) return new Match(MatchType.CONFLICT, null);
            GatewayCancellationTransaction value = exact.getFirst();
            return Objects.equals(value.amount(), start.cancelAmount())
                    ? new Match(MatchType.EXACT, value) : new Match(MatchType.CONFLICT, null);
        }
        List<GatewayCancellationTransaction> candidates = values.stream()
                .filter(value -> Objects.equals(value.amount(), start.cancelAmount()))
                .filter(value -> Objects.equals(normalize(value.reason()), normalize(start.reason())))
                .filter(value -> value.canceledAt() != null && !value.canceledAt().isBefore(start.requestedAt()))
                .toList();
        if (candidates.size() == 1) return new Match(MatchType.EXACT, candidates.getFirst());
        return new Match(candidates.isEmpty() ? MatchType.NONE : MatchType.AMBIGUOUS, null);
    }

    private boolean validIdentity(PartialCancellationReconciliationStart start, GatewayPaymentQueryResult query) {
        return query != null && Objects.equals(start.providerPaymentKey(), query.providerPaymentKey())
                && Objects.equals(start.merchantPaymentId(), query.merchantPaymentId())
                && Objects.equals(start.originalAmount(), query.amount())
                && Objects.equals(start.currency(), query.currency());
    }

    private boolean consistentStatus(GatewayPaymentStatus status, Long remaining) {
        return remaining != null && ((remaining == 0L && (status == GatewayPaymentStatus.CANCELED
                        || status == GatewayPaymentStatus.PARTIALLY_CANCELED))
                || (remaining > 0L && status == GatewayPaymentStatus.PARTIALLY_CANCELED));
    }

    private String validationType(RuntimeException exception) {
        return exception instanceof PartialCancellationValidationException validation
                ? validation.getValidationType() : "UNCLASSIFIED";
    }

    private PartialCancellationStart toCompletionStart(PartialCancellationReconciliationStart start) {
        return new PartialCancellationStart(PartialCancellationStart.Action.EXECUTE,
                start.orderCancellationId(), start.paymentId(), start.paymentCancellationId(), start.provider(),
                start.providerPaymentKey(), start.merchantPaymentId(), start.originalAmount(), start.cancelAmount(),
                start.currency(), start.reason(), start.idempotencyKey());
    }

    private String normalize(String value) { return value == null ? null : value.trim(); }

    private void logResult(PartialCancellationReconciliationStart start, long ageSeconds,
                           ReconciliationResult result) {
        log.info("Partial cancellation reconciliation checked. paymentId={}, paymentCancellationId={}, orderCancellationId={}, provider={}, ageSeconds={}, resultType={}",
                start.paymentId(), start.paymentCancellationId(), start.orderCancellationId(), start.provider(), ageSeconds, result);
    }

    private enum ReconciliationResult { COMPLETED, RETRY_SAFE, RETRY_COMPLETED, UNRESOLVED }
    private enum MatchType { EXACT, NONE, AMBIGUOUS, CONFLICT }
    private record Match(MatchType type, GatewayCancellationTransaction transaction) { }
}
