package com.giftmarket.payment.service;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentExpirationServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentExpirationTransactionService transactionService;

    @Test
    void processesLimitedCandidatesIndependently() {
        PaymentProperties properties = new PaymentProperties();
        properties.setExpirationBatchSize(2);
        PaymentExpirationService service = new PaymentExpirationService(
                paymentRepository,
                transactionService,
                properties
        );
        given(paymentRepository.findExpirationCandidateIds(
                eq(PaymentStatus.READY),
                eq(OrderStatus.PENDING_PAYMENT),
                any(),
                any(Pageable.class)
        )).willReturn(List.of(1L, 2L));
        given(transactionService.expireReadyPayment(eq(1L), any()))
                .willThrow(new IllegalStateException("first failed"));

        service.expirePayments();

        verify(transactionService).expireReadyPayment(eq(2L), any());
    }
}
