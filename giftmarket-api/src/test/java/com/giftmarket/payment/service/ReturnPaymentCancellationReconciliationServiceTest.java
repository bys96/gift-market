package com.giftmarket.payment.service;

import com.giftmarket.order.entity.ReturnRequest;
import com.giftmarket.order.service.ReturnCompletionService;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;

class ReturnPaymentCancellationReconciliationServiceTest {
    @Test
    void expiredRequestIsQueriedButNeverRetried() {
        var repository = mock(PaymentCancellationRepository.class);
        var transactions = mock(ReturnPaymentCancellationTransactionService.class);
        var registry = mock(PaymentGatewayRegistry.class);
        var gateway = mock(PaymentGateway.class);
        var completion = mock(ReturnCompletionService.class);
        var cancellation = mock(PaymentCancellation.class);
        var payment = mock(Payment.class);
        var request = mock(ReturnRequest.class);
        when(repository.findReturnCancellationForReconciliation(3L)).thenReturn(Optional.of(cancellation));
        when(cancellation.getReturnRequest()).thenReturn(request);
        when(cancellation.getStatus()).thenReturn(PaymentCancellationStatus.REQUESTED);
        when(cancellation.getPayment()).thenReturn(payment);
        when(cancellation.getRequestedAt()).thenReturn(LocalDateTime.now().minusDays(15));
        when(cancellation.getAmount()).thenReturn(100L);
        when(payment.getProvider()).thenReturn(PaymentProvider.TOSS);
        when(payment.getProviderPaymentKey()).thenReturn("key");
        when(payment.getMerchantPaymentId()).thenReturn("order");
        when(payment.getAmount()).thenReturn(1000L);
        when(payment.getCurrency()).thenReturn("KRW");
        when(registry.get(PaymentProvider.TOSS)).thenReturn(gateway);
        when(gateway.getPayment("key")).thenReturn(new GatewayPaymentQueryResult(
                GatewayPaymentStatus.PAID, "key", null, "order", 1000L, "KRW", null, null,
                "DONE", LocalDateTime.now(), 1000L, null, true, List.of()));
        new ReturnPaymentCancellationReconciliationService(repository, transactions, registry,
                new PaymentProperties(), completion).reconcile(3L);
        verify(gateway).getPayment("key");
        verify(gateway, never()).cancel(any());
        verifyNoInteractions(transactions, completion);
    }
}
