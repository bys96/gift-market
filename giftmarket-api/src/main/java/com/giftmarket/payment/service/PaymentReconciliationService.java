package com.giftmarket.payment.service;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.gateway.GatewayPaymentQueryResult;
import com.giftmarket.payment.gateway.PaymentGateway;
import com.giftmarket.payment.gateway.PaymentGatewayRegistry;
import com.giftmarket.payment.gateway.PaymentGatewayUncertainException;
import com.giftmarket.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionService transactionService;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentProperties paymentProperties;
    private final ReconciliationBatchExecutor batches;

    @Scheduled(
            fixedDelayString = "#{@paymentProperties.reconciliationCheckIntervalMillis}"
    )
    public void reconcilePayments() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime confirmingBefore = now.minusSeconds(
                paymentProperties.getReconciliationDelaySeconds()
        );
        batches.run(ReconciliationBatchExecutor.Kind.APPROVAL,
                paymentProperties.getReconciliationBatchSize(), paymentRepository::findMaxReconciliationId,
                (after, through, limit) -> paymentRepository.findReconciliationCandidateIds(
                        PaymentStatus.CONFIRMING,
                        OrderStatus.PENDING_PAYMENT,
                        confirmingBefore,
                        after, through, limit), Long::longValue, paymentId -> {
            try {
                reconcileOne(paymentId, confirmingBefore, now);
            } catch (RuntimeException exception) {
                log.error(
                        "Payment reconciliation failed. paymentId={}, exceptionType={}",
                        paymentId,
                        exception.getClass().getSimpleName()
                );
            }
        });
    }

    void reconcileOne(
            Long paymentId,
            LocalDateTime confirmingBefore,
            LocalDateTime now
    ) {
        PaymentConfirmStart start = transactionService.startReconciliation(
                paymentId,
                confirmingBefore
        );
        if (start.action() != PaymentConfirmStart.Action.QUERY) {
            return;
        }

        long ageSeconds = Math.max(
                0,
                Duration.between(start.confirmingAt(), now).getSeconds()
        );
        log.warn(
                "Long-running payment reconciliation started. paymentId={}, provider={}, ageSeconds={}",
                paymentId,
                start.provider(),
                ageSeconds
        );

        PaymentGateway gateway = gatewayRegistry.get(start.provider());
        try {
            GatewayPaymentQueryResult result = gateway.getPayment(
                    start.providerPaymentKey()
            );
            var applied = transactionService.reconcile(paymentId, result);
            log.info("Payment reconciliation checked. paymentId={}, resultType={}", paymentId,
                    applied.status() == PaymentStatus.CONFIRMING ? "UNRESOLVED" : "COMPLETED");
        } catch (PaymentGatewayUncertainException exception) {
            log.warn(
                    "Payment reconciliation remains uncertain. paymentId={}, provider={}, exceptionType={}",
                    paymentId,
                    start.provider(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
