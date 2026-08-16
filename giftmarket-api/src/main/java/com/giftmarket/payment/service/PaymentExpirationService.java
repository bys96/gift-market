package com.giftmarket.payment.service;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentExpirationService {

    private final PaymentRepository paymentRepository;
    private final PaymentExpirationTransactionService transactionService;
    private final PaymentProperties paymentProperties;

    @Scheduled(
            fixedDelayString = "#{@paymentProperties.expirationCheckIntervalMillis}"
    )
    public void expirePayments() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> candidateIds = paymentRepository.findExpirationCandidateIds(
                PaymentStatus.READY,
                OrderStatus.PENDING_PAYMENT,
                now,
                PageRequest.of(0, paymentProperties.getExpirationBatchSize())
        );

        for (Long paymentId : candidateIds) {
            try {
                transactionService.expireReadyPayment(paymentId, now);
            } catch (RuntimeException exception) {
                log.error(
                        "Payment expiration failed. paymentId={}",
                        paymentId,
                        exception
                );
            }
        }
    }
}
