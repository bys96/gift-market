package com.giftmarket.payment.service;

import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.ExchangeShippingPaymentStatus;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.ExchangeShippingPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeShippingPaymentReconciliationService {
    private final ExchangeShippingPaymentRepository repository;
    private final ExchangeShippingPaymentTransactionService transactionService;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentProperties properties;

    @Scheduled(fixedDelayString = "#{@paymentProperties.reconciliationCheckIntervalMillis}")
    public void reconcilePayments() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(properties.getReconciliationDelaySeconds());
        for (Long id : repository.findReconciliationCandidateIds(ExchangeShippingPaymentStatus.REQUESTED, before,
                PageRequest.of(0, properties.getReconciliationBatchSize()))) {
            try { reconcileOne(id, before); }
            catch (RuntimeException exception) { log.error("Exchange shipping payment reconciliation failed. paymentId={}, exceptionType={}", id, exception.getClass().getSimpleName()); }
        }
    }

    public void reconcileOne(Long paymentId, LocalDateTime before) {
        ExchangeShippingPaymentStart start = transactionService.startReconciliation(paymentId, before);
        if (start.action() != ExchangeShippingPaymentStart.Action.QUERY) return;
        try {
            PaymentGateway gateway = gatewayRegistry.get(start.provider());
            GatewayPaymentQueryResult result = start.providerPaymentKey() == null
                    ? gateway.getPaymentByOrderId(start.providerOrderId())
                    : gateway.getPayment(start.providerPaymentKey());
            transactionService.apply(paymentId, result);
        } catch (PaymentGatewayUncertainException exception) {
            log.warn("Exchange shipping payment remains uncertain. paymentId={}", paymentId);
        }
    }
}
