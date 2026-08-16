package com.giftmarket.payment.service;

import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.entity.PaymentWebhookEvent;
import com.giftmarket.payment.entity.PaymentWebhookStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.payment.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookEventServiceTest {

    @Mock PaymentWebhookEventRepository eventRepository;
    @Mock PaymentRepository paymentRepository;

    @Test
    void processedTransmissionIsImmediateDuplicate() {
        PaymentWebhookEvent event = mock(PaymentWebhookEvent.class);
        given(event.getStatus()).willReturn(PaymentWebhookStatus.PROCESSED);
        given(eventRepository.findByProviderAndExternalEventId(
                PaymentProvider.TOSS,
                "event-id"
        )).willReturn(Optional.of(event));
        PaymentWebhookEventService service = new PaymentWebhookEventService(
                eventRepository,
                paymentRepository
        );

        assertThat(service.begin(
                PaymentProvider.TOSS,
                "event-id",
                "PAYMENT_STATUS_CHANGED"
        )).isEqualTo(PaymentWebhookEventService.BeginResult.DUPLICATE);

        verify(event, never()).restart(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retryableFailureCanBeProcessedAgain() {
        PaymentWebhookEvent event = mock(PaymentWebhookEvent.class);
        given(event.getStatus()).willReturn(
                PaymentWebhookStatus.RETRYABLE_FAILED
        );
        given(eventRepository.findByProviderAndExternalEventId(
                PaymentProvider.TOSS,
                "event-id"
        )).willReturn(Optional.of(event));
        PaymentWebhookEventService service = new PaymentWebhookEventService(
                eventRepository,
                paymentRepository
        );

        assertThat(service.begin(
                PaymentProvider.TOSS,
                "event-id",
                "PAYMENT_STATUS_CHANGED"
        )).isEqualTo(PaymentWebhookEventService.BeginResult.PROCESS);

        verify(event).restart(org.mockito.ArgumentMatchers.any());
    }
}
