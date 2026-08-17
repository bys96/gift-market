package com.giftmarket.payment.service;

public record PaymentRefundBalance(
        long originalAmount,
        long succeededRefundAmount,
        long reservedRefundAmount,
        long availableRefundAmount
) {
}
