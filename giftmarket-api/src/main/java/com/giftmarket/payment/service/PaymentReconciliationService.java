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
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionService transactionService;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentProperties paymentProperties;

    @Scheduled(
            fixedDelayString = "#{@paymentProperties.reconciliationCheckIntervalMillis}"
    )
    public void reconcilePayments() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime confirmingBefore = now.minusSeconds(
                paymentProperties.getReconciliationDelaySeconds()
        );
        List<Long> candidateIds = paymentRepository
                .findReconciliationCandidateIds(
                        PaymentStatus.CONFIRMING,
                        OrderStatus.PENDING_PAYMENT,
                        confirmingBefore,
                        PageRequest.of(
                                0,
                                paymentProperties.getReconciliationBatchSize()
                        )
                );

        for (Long paymentId : candidateIds) {
            try {
                reconcileOne(paymentId, confirmingBefore, now);
            } catch (RuntimeException exception) {
                log.error(
                        "Payment reconciliation failed. paymentId={}, exceptionType={}",
                        paymentId,
                        exception.getClass().getSimpleName()
                );
            }
        }
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
            transactionService.reconcile(paymentId, result);
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
