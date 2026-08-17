package com.giftmarket.payment.entity;

public enum PaymentStatus {

    READY,
    CONFIRMING,
    PAID,
    PARTIALLY_CANCELED,
    FAILED,
    EXPIRED,
    CANCELING,
    CANCELED
}
