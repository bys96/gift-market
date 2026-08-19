package com.giftmarket.payment.service;

import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PaymentRefundBalanceServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentCancellationRepository cancellationRepository;
    private PaymentRefundBalanceService service;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new PaymentRefundBalanceService(paymentRepository, cancellationRepository);
        payment = mock(Payment.class);
        given(payment.getId()).willReturn(1L);
        given(payment.getAmount()).willReturn(30_000L);
    }

    @Test
    void calculatesSucceededReservedAndAvailableAmounts() {
        given(cancellationRepository.sumAmountByPaymentIdAndStatus(1L, PaymentCancellationStatus.SUCCEEDED))
                .willReturn(10_000L);
        given(cancellationRepository.sumAmountByPaymentIdAndStatus(1L, PaymentCancellationStatus.REQUESTED))
                .willReturn(5_000L);

        PaymentRefundBalance balance = service.getBalance(payment);

        assertThat(balance.originalAmount()).isEqualTo(30_000L);
        assertThat(balance.succeededRefundAmount()).isEqualTo(10_000L);
        assertThat(balance.reservedRefundAmount()).isEqualTo(5_000L);
        assertThat(balance.availableRefundAmount()).isEqualTo(15_000L);
        service.validateRefundAmount(balance, 15_000L);
        assertThatThrownBy(() -> service.validateRefundAmount(balance, 15_001L))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void treatsNullAggregatesAsZeroAndExcludesFailedByQueryDesign() {
        given(cancellationRepository.sumAmountByPaymentIdAndStatus(1L, PaymentCancellationStatus.SUCCEEDED))
                .willReturn(null);
        given(cancellationRepository.sumAmountByPaymentIdAndStatus(1L, PaymentCancellationStatus.REQUESTED))
                .willReturn(null);

        PaymentRefundBalance balance = service.getBalance(payment);

        assertThat(balance.succeededRefundAmount()).isZero();
        assertThat(balance.originalAmount() - balance.succeededRefundAmount())
                .isEqualTo(30_000L);
    }

    @Test
    void reportsAccumulatedSucceededRefundAndCurrentPaidBalance() {
        given(cancellationRepository.sumAmountByPaymentIdAndStatus(
                1L, PaymentCancellationStatus.SUCCEEDED
        )).willReturn(20_000L);
        given(cancellationRepository.sumAmountByPaymentIdAndStatus(
                1L, PaymentCancellationStatus.REQUESTED
        )).willReturn(5_000L);

        PaymentRefundBalance balance = service.getBalance(payment);

        assertThat(balance.succeededRefundAmount()).isEqualTo(20_000L);
        assertThat(balance.originalAmount() - balance.succeededRefundAmount())
                .isEqualTo(10_000L);
        assertThat(balance.availableRefundAmount()).isEqualTo(5_000L);
    }

    @Test
    void reportsZeroBalanceAfterSucceededRefundCoversOriginalAmount() {
        given(cancellationRepository.sumAmountByPaymentIdAndStatus(
                1L, PaymentCancellationStatus.SUCCEEDED
        )).willReturn(30_000L);
        given(cancellationRepository.sumAmountByPaymentIdAndStatus(
                1L, PaymentCancellationStatus.REQUESTED
        )).willReturn(0L);

        PaymentRefundBalance balance = service.getBalance(payment);

        assertThat(balance.succeededRefundAmount()).isEqualTo(30_000L);
        assertThat(balance.originalAmount() - balance.succeededRefundAmount()).isZero();
        assertThat(balance.availableRefundAmount()).isZero();
    }

    @Test
    void rejectsOverflowAndInconsistentTotal() {
        given(cancellationRepository.sumAmountByPaymentIdAndStatus(1L, PaymentCancellationStatus.SUCCEEDED))
                .willReturn(Long.MAX_VALUE);
        given(cancellationRepository.sumAmountByPaymentIdAndStatus(1L, PaymentCancellationStatus.REQUESTED))
                .willReturn(1L);

        assertThatThrownBy(() -> service.getBalance(payment)).isInstanceOf(PaymentException.class);
    }
}
