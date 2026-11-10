package com.giftmarket.payment.service;

import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.ExchangeShippingPaymentRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;

class ExchangeShippingPaymentReconciliationServiceTest {
    private final ExchangeShippingPaymentRepository repository = mock(ExchangeShippingPaymentRepository.class);
    private final ExchangeShippingPaymentTransactionService transactions = mock(ExchangeShippingPaymentTransactionService.class);
    private final PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final PaymentProperties properties = new PaymentProperties();
    private final ExchangeShippingPaymentReconciliationService service =
            new ExchangeShippingPaymentReconciliationService(repository, transactions, registry, properties,
                    ReconciliationBatchTestSupport.executor());
    private final LocalDateTime before = LocalDateTime.of(2026, 8, 24, 13, 0);

    @Test
    void schedulerRotatesPastUnresolvedShippingPayments() {
        properties.setReconciliationBatchSize(2);
        when(repository.findMaxReconciliationId()).thenReturn(3L);
        when(repository.findReconciliationCandidateIds(any(), any(), anyLong(), anyLong(), any())).thenAnswer(invocation -> {
            long after = invocation.getArgument(2);
            return List.of(1L, 2L, 3L).stream().filter(id -> id > after).limit(2).toList();
        });
        when(transactions.startReconciliation(anyLong(), any())).thenReturn(new ExchangeShippingPaymentStart(
                ExchangeShippingPaymentStart.Action.COMPLETED, null, null, null, null, 0L, null, null));
        service.reconcilePayments(); service.reconcilePayments();
        verify(transactions).startReconciliation(eq(3L), any());
        verifyNoInteractions(registry, gateway);
    }

    @Test
    void requestedWithoutPaymentKeyFallsBackToOrderId() {
        ExchangeShippingPaymentStart start = start(null);
        GatewayPaymentQueryResult result = unknown(null);
        when(transactions.startReconciliation(10L, before)).thenReturn(start);
        when(registry.get(PaymentProvider.TOSS)).thenReturn(gateway);
        when(gateway.getPaymentByOrderId("exchange-order-1")).thenReturn(result);

        service.reconcileOne(10L, before);

        verify(gateway).getPaymentByOrderId("exchange-order-1");
        verify(gateway, never()).getPayment(any());
        verify(transactions).apply(10L, result);
    }

    @Test
    void requestedWithPaymentKeyUsesPaymentKeyLookup() {
        ExchangeShippingPaymentStart start = start("payment-key");
        GatewayPaymentQueryResult result = unknown("payment-key");
        when(transactions.startReconciliation(10L, before)).thenReturn(start);
        when(registry.get(PaymentProvider.TOSS)).thenReturn(gateway);
        when(gateway.getPayment("payment-key")).thenReturn(result);

        service.reconcileOne(10L, before);

        verify(gateway).getPayment("payment-key");
        verify(gateway, never()).getPaymentByOrderId(any());
        verify(transactions).apply(10L, result);
    }

    private ExchangeShippingPaymentStart start(String paymentKey) {
        return new ExchangeShippingPaymentStart(ExchangeShippingPaymentStart.Action.QUERY, 10L,
                PaymentProvider.TOSS, paymentKey, "exchange-order-1", 6_000, "idempotency-1", null);
    }

    private GatewayPaymentQueryResult unknown(String paymentKey) {
        return new GatewayPaymentQueryResult(GatewayPaymentStatus.UNKNOWN, paymentKey, null, null,
                null, null, null, null, null, null, null, null, null, List.of());
    }
}
