package com.giftmarket.payment.gateway;

import com.giftmarket.payment.entity.EasyPayProvider;
import com.giftmarket.payment.entity.PaymentMethod;

import java.time.LocalDateTime;
import java.util.List;

public record GatewayPaymentQueryResult(

        GatewayPaymentStatus status,
        String providerPaymentKey,
        String providerTransactionId,
        String merchantPaymentId,
        Long amount,
        String currency,
        PaymentMethod method,
        EasyPayProvider easyPayProvider,
        String providerStatus,
        LocalDateTime approvedAt,
        Long remainingAmount,
        LocalDateTime canceledAt,
        Boolean partialCancelable,
        List<GatewayCancellationTransaction> cancellations

) {
    public GatewayPaymentQueryResult(
            GatewayPaymentStatus status, String providerPaymentKey,
            String providerTransactionId, String merchantPaymentId,
            Long amount, String currency, PaymentMethod method,
            EasyPayProvider easyPayProvider, String providerStatus,
            LocalDateTime approvedAt
    ) {
        this(status, providerPaymentKey, providerTransactionId,
                merchantPaymentId, amount, currency, method,
                easyPayProvider, providerStatus, approvedAt, null, null, null, List.of());
    }

    public GatewayPaymentQueryResult(
            GatewayPaymentStatus status, String providerPaymentKey,
            String providerTransactionId, String merchantPaymentId,
            Long amount, String currency, PaymentMethod method,
            EasyPayProvider easyPayProvider, String providerStatus,
            LocalDateTime approvedAt, Long remainingAmount, LocalDateTime canceledAt
    ) {
        this(status, providerPaymentKey, providerTransactionId, merchantPaymentId,
                amount, currency, method, easyPayProvider, providerStatus,
                approvedAt, remainingAmount, canceledAt, null, List.of());
    }

    public GatewayPaymentQueryResult(
            GatewayPaymentStatus status, String providerPaymentKey,
            String providerTransactionId, String merchantPaymentId,
            Long amount, String currency, PaymentMethod method,
            EasyPayProvider easyPayProvider, String providerStatus,
            LocalDateTime approvedAt, Long remainingAmount, LocalDateTime canceledAt,
            Boolean partialCancelable
    ) {
        this(status, providerPaymentKey, providerTransactionId, merchantPaymentId,
                amount, currency, method, easyPayProvider, providerStatus, approvedAt,
                remainingAmount, canceledAt, partialCancelable, List.of());
    }
}
