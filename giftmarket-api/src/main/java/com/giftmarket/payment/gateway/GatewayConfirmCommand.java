package com.giftmarket.payment.gateway;

public record GatewayConfirmCommand(

        String providerPaymentKey,
        String merchantPaymentId,
        Long amount,
        String currency,
        String idempotencyKey

) {
}
