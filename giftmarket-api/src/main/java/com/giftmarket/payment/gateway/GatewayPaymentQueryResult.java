package com.giftmarket.payment.gateway;

import com.giftmarket.payment.entity.EasyPayProvider;
import com.giftmarket.payment.entity.PaymentMethod;

import java.time.LocalDateTime;

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
        LocalDateTime approvedAt

) {
}
