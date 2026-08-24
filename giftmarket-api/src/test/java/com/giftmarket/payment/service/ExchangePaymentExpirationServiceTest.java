package com.giftmarket.payment.service;

import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.ExchangeShippingPayment;
import com.giftmarket.payment.entity.ExchangeShippingPaymentStatus;
import com.giftmarket.payment.repository.ExchangeShippingPaymentRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExchangePaymentExpirationServiceTest {
    private final ExchangeRequestRepository exchanges = mock(ExchangeRequestRepository.class);
    private final ExchangeShippingPaymentRepository payments = mock(ExchangeShippingPaymentRepository.class);
    private final ExchangeShippingPaymentReconciliationService reconciliation = mock(ExchangeShippingPaymentReconciliationService.class);
    private final ExchangePaymentExpirationTransactionService transactions = mock(ExchangePaymentExpirationTransactionService.class);
    private final ExchangePaymentExpirationService service = new ExchangePaymentExpirationService(
            exchanges, payments, reconciliation, transactions, new PaymentProperties());
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 25, 13, 0);

    @Test
    void failedPaymentCanExpireWithoutReconciliation() {
        ExchangeShippingPayment payment = payment(ExchangeShippingPaymentStatus.FAILED, 10L);
        when(payments.findByExchangeRequestId(1L)).thenReturn(Optional.of(payment));
        when(transactions.expire(1L, now)).thenReturn(true);
        assertThat(service.expireOne(1L, now)).isTrue();
        verifyNoInteractions(reconciliation);
    }

    @Test
    void requestedPaymentIsReconciledAndUnresolvedExpirationDoesNotRelease() {
        ExchangeShippingPayment payment = payment(ExchangeShippingPaymentStatus.REQUESTED, 10L);
        when(payments.findByExchangeRequestId(1L)).thenReturn(Optional.of(payment));
        when(transactions.expire(1L, now)).thenReturn(false);
        assertThat(service.expireOne(1L, now)).isFalse();
        verify(reconciliation).reconcileOne(10L, now);
        verify(transactions).expire(1L, now);
    }

    @Test
    void succeededAndCompensationRequiredAreNotReconciledOrExpired() {
        for (ExchangeShippingPaymentStatus status : new ExchangeShippingPaymentStatus[]{
                ExchangeShippingPaymentStatus.SUCCEEDED,
                ExchangeShippingPaymentStatus.COMPENSATION_REQUIRED}) {
            reset(payments, reconciliation, transactions);
            ExchangeShippingPayment payment = payment(status, 10L);
            when(payments.findByExchangeRequestId(1L)).thenReturn(Optional.of(payment));
            when(transactions.expire(1L, now)).thenReturn(false);
            assertThat(service.expireOne(1L, now)).isFalse();
            verifyNoInteractions(reconciliation);
        }
    }

    private ExchangeShippingPayment payment(ExchangeShippingPaymentStatus status, Long id) {
        ExchangeShippingPayment payment = mock(ExchangeShippingPayment.class);
        when(payment.getStatus()).thenReturn(status);
        when(payment.getId()).thenReturn(id);
        return payment;
    }
}
