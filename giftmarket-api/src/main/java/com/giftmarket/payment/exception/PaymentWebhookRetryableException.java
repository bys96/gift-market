package com.giftmarket.payment.exception;

public class PaymentWebhookRetryableException extends RuntimeException {

    public PaymentWebhookRetryableException(String message) {
        super(message);
    }
}
