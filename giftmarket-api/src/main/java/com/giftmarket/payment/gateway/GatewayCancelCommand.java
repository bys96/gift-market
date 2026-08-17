package com.giftmarket.payment.gateway;

public record GatewayCancelCommand(String providerPaymentKey, String merchantPaymentId,
                                   Long amount, String currency, String reason, String idempotencyKey) {
}
