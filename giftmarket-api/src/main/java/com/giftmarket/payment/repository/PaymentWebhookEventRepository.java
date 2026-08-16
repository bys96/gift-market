package com.giftmarket.payment.repository;

import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.entity.PaymentWebhookEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PaymentWebhookEventRepository
        extends JpaRepository<PaymentWebhookEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentWebhookEvent> findByProviderAndExternalEventId(
            PaymentProvider provider,
            String externalEventId
    );
}
