package com.giftmarket.payment.gateway;

public record GatewayCancelCommand(String providerPaymentKey, String merchantPaymentId,
                                   Long amount, String currency, String reason, String idempotencyKey,
                                   Long cancelAmount) {

    public GatewayCancelCommand(String providerPaymentKey, String merchantPaymentId,
                                Long amount, String currency, String reason, String idempotencyKey) {
        this(providerPaymentKey, merchantPaymentId, amount, currency, reason, idempotencyKey, null);
    }

    public static GatewayCancelCommand partial(
            String providerPaymentKey, String merchantPaymentId, Long originalAmount,
            String currency, String reason, String idempotencyKey, Long cancelAmount
    ) {
        if (cancelAmount == null || cancelAmount <= 0L) {
            throw new IllegalArgumentException("부분취소 금액은 1원 이상이어야 합니다.");
        }
        return new GatewayCancelCommand(providerPaymentKey, merchantPaymentId, originalAmount,
                currency, reason, idempotencyKey, cancelAmount);
    }

    public boolean isPartialCancellation() {
        return cancelAmount != null;
    }
}
