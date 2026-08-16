package com.giftmarket.payment.service;

import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.entity.PaymentWebhookEvent;
import com.giftmarket.payment.entity.PaymentWebhookStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.payment.repository.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentWebhookEventService {

    private final PaymentWebhookEventRepository eventRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BeginResult begin(
            PaymentProvider provider,
            String externalEventId,
            String eventType
    ) {
        PaymentWebhookEvent existing = eventRepository
                .findByProviderAndExternalEventId(provider, externalEventId)
                .orElse(null);

        if (existing == null) {
            eventRepository.saveAndFlush(PaymentWebhookEvent.create(
                    provider,
                    externalEventId,
                    eventType,
                    LocalDateTime.now()
            ));
            return BeginResult.PROCESS;
        }
        if (existing.getStatus() == PaymentWebhookStatus.PROCESSED
                || existing.getStatus() == PaymentWebhookStatus.IGNORED
                || existing.getStatus() == PaymentWebhookStatus.REJECTED) {
            return BeginResult.DUPLICATE;
        }
        if (existing.getStatus() == PaymentWebhookStatus.PROCESSING) {
            return BeginResult.IN_PROGRESS;
        }

        existing.restart(LocalDateTime.now());
        return BeginResult.PROCESS;
    }

    @Transactional(readOnly = true)
    public TargetResult findTarget(
            PaymentProvider provider,
            String merchantPaymentId,
            String providerPaymentKey
    ) {
        Payment merchantPayment = paymentRepository
                .findByMerchantPaymentId(merchantPaymentId)
                .orElse(null);
        if (merchantPayment == null) {
            return TargetResult.notFound();
        }
        if (merchantPayment.getProvider() != provider) {
            return TargetResult.mismatch(merchantPayment.getId());
        }

        Payment keyPayment = paymentRepository
                .findByProviderAndProviderPaymentKey(
                        provider,
                        providerPaymentKey
                )
                .orElse(null);
        if (keyPayment != null
                && !keyPayment.getId().equals(merchantPayment.getId())) {
            return TargetResult.mismatch(merchantPayment.getId());
        }
        if (merchantPayment.getProviderPaymentKey() != null
                && !merchantPayment.getProviderPaymentKey().equals(
                providerPaymentKey
        )) {
            return TargetResult.mismatch(merchantPayment.getId());
        }
        return TargetResult.found(merchantPayment.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processed(
            PaymentProvider provider,
            String externalEventId,
            Long paymentId
    ) {
        event(provider, externalEventId).processed(
                paymentRepository.getReferenceById(paymentId),
                LocalDateTime.now()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ignored(
            PaymentProvider provider,
            String externalEventId,
            String reason
    ) {
        event(provider, externalEventId).ignored(reason, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(
            PaymentProvider provider,
            String externalEventId,
            Long paymentId,
            String reason
    ) {
        Payment payment = paymentId == null
                ? null
                : paymentRepository.getReferenceById(paymentId);
        event(provider, externalEventId).rejected(
                payment,
                reason,
                LocalDateTime.now()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryableFailure(
            PaymentProvider provider,
            String externalEventId,
            String reason
    ) {
        event(provider, externalEventId).retryableFailure(
                reason,
                LocalDateTime.now()
        );
    }

    private PaymentWebhookEvent event(
            PaymentProvider provider,
            String externalEventId
    ) {
        return eventRepository
                .findByProviderAndExternalEventId(provider, externalEventId)
                .orElseThrow();
    }

    public enum BeginResult {
        PROCESS,
        DUPLICATE,
        IN_PROGRESS
    }

    public record TargetResult(
            TargetStatus status,
            Long paymentId
    ) {
        static TargetResult found(Long paymentId) {
            return new TargetResult(TargetStatus.FOUND, paymentId);
        }

        static TargetResult notFound() {
            return new TargetResult(TargetStatus.NOT_FOUND, null);
        }

        static TargetResult mismatch(Long paymentId) {
            return new TargetResult(TargetStatus.MISMATCH, paymentId);
        }
    }

    public enum TargetStatus {
        FOUND,
        NOT_FOUND,
        MISMATCH
    }
}
