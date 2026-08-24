package com.giftmarket.payment.service;

import com.giftmarket.order.entity.ExchangeRequestStatus;
import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.ExchangeShippingPaymentStatus;
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
public class ExchangePaymentExpirationService {
    private final ExchangeRequestRepository exchangeRepository;
    private final ExchangeShippingPaymentRepository paymentRepository;
    private final ExchangeShippingPaymentReconciliationService reconciliationService;
    private final ExchangePaymentExpirationTransactionService transactionService;
    private final PaymentProperties properties;

    @Scheduled(fixedDelayString = "#{@paymentProperties.expirationCheckIntervalMillis}")
    public void expirePayments() {
        LocalDateTime now = LocalDateTime.now();
        for (Long requestId : exchangeRepository.findExpiredPaymentCandidateIds(
                ExchangeRequestStatus.PAYMENT_PENDING, now, PageRequest.of(0, properties.getExpirationBatchSize()))) {
            try { expireOne(requestId, now); }
            catch (RuntimeException exception) { log.error("Exchange payment expiration failed. exchangeRequestId={}, exceptionType={}", requestId, exception.getClass().getSimpleName()); }
        }
    }

    public boolean expireOne(Long requestId, LocalDateTime now) {
        paymentRepository.findByExchangeRequestId(requestId)
                .filter(payment -> payment.getStatus() == ExchangeShippingPaymentStatus.REQUESTED)
                .ifPresent(payment -> reconciliationService.reconcileOne(payment.getId(), now));
        return transactionService.expire(requestId, now);
    }
}
