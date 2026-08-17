package com.giftmarket.payment.service;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
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
public class PaymentCancellationReconciliationService {

    private static final long TOSS_IDEMPOTENCY_VALID_DAYS = 15;

    private final PaymentCancellationRepository cancellationRepository;
    private final PaymentCancellationTransactionService transactionService;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentProperties paymentProperties;

    @Scheduled(
            fixedDelayString = "#{@paymentProperties.cancelReconciliationCheckIntervalMillis}"
    )
    public void reconcileCancellations() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime requestedBefore = now.minusSeconds(
                paymentProperties.getCancelReconciliationDelaySeconds()
        );
        List<Long> candidatePaymentIds = cancellationRepository
                .findCancelReconciliationCandidatePaymentIds(
                        PaymentCancellationStatus.REQUESTED,
                        PaymentStatus.CANCELING,
                        OrderStatus.PAID,
                        requestedBefore,
                        PageRequest.of(
                                0,
                                paymentProperties.getCancelReconciliationBatchSize()
                        )
                );

        for (Long paymentId : candidatePaymentIds) {
            try {
                reconcileOne(paymentId, requestedBefore, now);
            } catch (RuntimeException exception) {
                log.error(
                        "Payment cancellation reconciliation failed. paymentId={}, exceptionType={}",
                        paymentId,
                        exception.getClass().getSimpleName()
                );
            }
        }
    }

    void reconcileOne(
            Long paymentId,
            LocalDateTime requestedBefore,
            LocalDateTime now
    ) {
        PaymentCancellationReconciliationStart start =
                transactionService.startReconciliation(
                        paymentId,
                        requestedBefore
                );
        if (start.action()
                != PaymentCancellationReconciliationStart.Action.QUERY) {
            return;
        }

        long ageSeconds = Math.max(
                0,
                Duration.between(start.requestedAt(), now).getSeconds()
        );
        log.warn(
                "Long-running cancellation reconciliation started. paymentId={}, cancellationId={}, provider={}, ageSeconds={}",
                start.paymentId(),
                start.cancellationId(),
                start.provider(),
                ageSeconds
        );

        PaymentGateway gateway = gatewayRegistry.get(start.provider());
        try {
            GatewayPaymentQueryResult queryResult = gateway.getPayment(
                    start.providerPaymentKey()
            );

            if (queryResult.status() == GatewayPaymentStatus.CANCELED
                    && Objects.equals(queryResult.remainingAmount(), 0L)) {
                transactionService.completeFromReconciliationQuery(
                        start.paymentId(),
                        start.cancellationId(),
                        queryResult
                );
                log.info(
                        "Cancellation reconciliation completed from provider query. paymentId={}, cancellationId={}",
                        start.paymentId(),
                        start.cancellationId()
                );
                return;
            }

            if (queryResult.status() != GatewayPaymentStatus.PAID) {
                log.warn(
                        "Cancellation reconciliation remains unresolved. paymentId={}, cancellationId={}, queryResult={}",
                        start.paymentId(),
                        start.cancellationId(),
                        queryResult.status()
                );
                return;
            }

            if (!start.requestedAt().plusDays(TOSS_IDEMPOTENCY_VALID_DAYS)
                    .isAfter(now)) {
                log.warn(
                        "Cancellation automatic retry skipped because idempotency validity elapsed. paymentId={}, cancellationId={}, ageSeconds={}",
                        start.paymentId(),
                        start.cancellationId(),
                        ageSeconds
                );
                return;
            }

            GatewayCancelResult cancelResult = gateway.cancel(
                    new GatewayCancelCommand(
                            start.providerPaymentKey(),
                            start.merchantPaymentId(),
                            start.amount(),
                            start.currency(),
                            start.reason(),
                            start.idempotencyKey()
                    )
            );
            if (cancelResult.status() == GatewayPaymentStatus.CANCELED
                    && Objects.equals(cancelResult.remainingAmount(), 0L)) {
                transactionService.completeFromReconciliationCancel(
                        start.paymentId(),
                        start.cancellationId(),
                        cancelResult
                );
            } else {
                log.warn(
                        "Cancellation retry response remains unresolved. paymentId={}, cancellationId={}, result={}",
                        start.paymentId(),
                        start.cancellationId(),
                        cancelResult.status()
                );
            }
        } catch (PaymentGatewayDeclinedException exception) {
            transactionService.explicitReconciliationFailure(
                    start.paymentId(),
                    start.cancellationId(),
                    exception.getFailureCode(),
                    exception.getMessage()
            );
            log.warn(
                    "Cancellation reconciliation was explicitly declined. paymentId={}, cancellationId={}, failureCode={}",
                    start.paymentId(),
                    start.cancellationId(),
                    exception.getFailureCode()
            );
        } catch (PaymentGatewayUncertainException exception) {
            log.warn(
                    "Cancellation reconciliation remains uncertain. paymentId={}, cancellationId={}, provider={}, exceptionType={}",
                    start.paymentId(),
                    start.cancellationId(),
                    start.provider(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
