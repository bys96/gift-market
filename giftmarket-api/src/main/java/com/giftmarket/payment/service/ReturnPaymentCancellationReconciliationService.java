package com.giftmarket.payment.service;

import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.order.service.ReturnCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnPaymentCancellationReconciliationService {
    private final PaymentCancellationRepository cancellationRepository;
    private final ReturnPaymentCancellationTransactionService transactions;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentProperties properties;
    private final ReturnCompletionService completionService;

    @Scheduled(fixedDelayString = "#{@paymentProperties.partialCancellationReconciliationCheckIntervalMillis}")
    public void reconcileReturns() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(
                properties.getPartialCancellationReconciliationDelaySeconds());
        for (Long id : cancellationRepository.findReturnReconciliationCandidateIds(before,
                PageRequest.of(0, properties.getPartialCancellationReconciliationBatchSize()))) {
            try {
                reconcile(id);
            } catch (RuntimeException exception) {
                log.error("Return refund reconciliation failed. paymentCancellationId={}, exceptionType={}",
                        id, exception.getClass().getSimpleName());
            }
        }
    }

    public void reconcile(Long paymentCancellationId) {
        PaymentCancellation value = cancellationRepository.findReturnCancellationForReconciliation(paymentCancellationId).orElse(null);
        if (!eligible(value)) return;
        ReturnCancellationStart start = toStart(value);
        PaymentGateway gateway = gatewayRegistry.get(start.provider());
        try {
            GatewayPaymentQueryResult query = gateway.getPayment(start.providerPaymentKey());
            ReconcileResult result = reconcileQuery(start, value, query);
            if (result == ReconcileResult.RETRY_SAFE) {
                GatewayCancelResult canceled = gateway.cancel(GatewayCancelCommand.partial(
                        start.providerPaymentKey(), start.merchantPaymentId(), start.originalAmount(), start.currency(),
                        start.reason(), start.idempotencyKey(), start.cancelAmount()));
                if (canceled == null) throw new PaymentGatewayUncertainException("PG 환불 응답이 비어 있습니다.", null);
                transactions.complete(start, canceled);
                completionService.complete(start.returnRequestId());
            }
        } catch (PaymentGatewayDeclinedException exception) {
            transactions.fail(start.returnRequestId(), start.paymentCancellationId(), exception.getFailureCode(), exception.getMessage());
        } catch (PaymentGatewayUncertainException exception) {
            log.warn("Return refund reconciliation remains uncertain. paymentCancellationId={}", paymentCancellationId);
        }
    }

    public void reconcileFromWebhook(Long paymentId, GatewayPaymentQueryResult query) {
        for (Long id : cancellationRepository.findRequestedReturnPartialIdsByPaymentId(paymentId)) {
            PaymentCancellation value = cancellationRepository.findReturnCancellationForReconciliation(id).orElse(null);
            if (eligible(value)) reconcileQuery(toStart(value), value, query);
        }
    }

    public boolean hasRequestedReturnCancellation(Long paymentId) {
        return !cancellationRepository.findRequestedReturnPartialIdsByPaymentId(paymentId).isEmpty();
    }

    private ReconcileResult reconcileQuery(ReturnCancellationStart start, PaymentCancellation value,
                                            GatewayPaymentQueryResult query) {
        if (!validIdentity(start, query)) return ReconcileResult.UNRESOLVED;
        List<GatewayCancellationTransaction> candidates = (query.cancellations() == null ? List.<GatewayCancellationTransaction>of() : query.cancellations())
                .stream().filter(tx -> Objects.equals(tx.amount(), start.cancelAmount()))
                .filter(tx -> Objects.equals(normalize(tx.reason()), normalize(start.reason())))
                .filter(tx -> tx.canceledAt() != null && !tx.canceledAt().isBefore(value.getRequestedAt()))
                .toList();
        if (value.getProviderTransactionKey() != null && !value.getProviderTransactionKey().isBlank()) {
            candidates = candidates.stream().filter(tx -> value.getProviderTransactionKey().equals(tx.providerTransactionId())).toList();
        }
        if (candidates.size() > 1) return ReconcileResult.UNRESOLVED;
        if (candidates.size() == 1) {
            GatewayCancellationTransaction tx = candidates.getFirst();
            if (!"DONE".equalsIgnoreCase(tx.status()) || !Objects.equals(tx.remainingAmount(), query.remainingAmount()))
                return ReconcileResult.UNRESOLVED;
            transactions.complete(start, new GatewayCancelResult(query.status(), query.providerPaymentKey(),
                    tx.providerTransactionId(), query.merchantPaymentId(), query.amount(), query.remainingAmount(),
                    query.currency(), query.providerStatus(), tx.canceledAt(), tx.amount(), tx.status(), tx.remainingAmount()));
            completionService.complete(start.returnRequestId());
            return ReconcileResult.COMPLETED;
        }
        boolean safe = (query.status() == GatewayPaymentStatus.PAID || query.status() == GatewayPaymentStatus.PARTIALLY_CANCELED)
                && Boolean.TRUE.equals(query.partialCancelable());
        return safe ? ReconcileResult.RETRY_SAFE : ReconcileResult.UNRESOLVED;
    }

    private boolean eligible(PaymentCancellation value) {
        return value != null && value.getReturnRequest() != null && value.getOrderCancellation() == null
                && value.getStatus() == PaymentCancellationStatus.REQUESTED;
    }
    private ReturnCancellationStart toStart(PaymentCancellation value) {
        var payment = value.getPayment();
        return new ReturnCancellationStart(ReturnCancellationStart.Action.RECONCILE, value.getReturnRequest().getId(),
                payment.getId(), value.getId(), payment.getProvider(), payment.getProviderPaymentKey(),
                payment.getMerchantPaymentId(), payment.getAmount(), value.getAmount(), payment.getCurrency(),
                value.getReason(), value.getIdempotencyKey());
    }
    private boolean validIdentity(ReturnCancellationStart start, GatewayPaymentQueryResult query) {
        return query != null && Objects.equals(start.providerPaymentKey(), query.providerPaymentKey())
                && Objects.equals(start.merchantPaymentId(), query.merchantPaymentId())
                && Objects.equals(start.originalAmount(), query.amount()) && Objects.equals(start.currency(), query.currency());
    }
    private String normalize(String value) { return value == null ? null : value.trim(); }
    private enum ReconcileResult { COMPLETED, RETRY_SAFE, UNRESOLVED }
}
