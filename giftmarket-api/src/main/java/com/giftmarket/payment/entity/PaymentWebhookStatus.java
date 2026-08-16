package com.giftmarket.payment.entity;

public enum PaymentWebhookStatus {

    PROCESSING,
    PROCESSED,
    IGNORED,
    REJECTED,
    RETRYABLE_FAILED
}
