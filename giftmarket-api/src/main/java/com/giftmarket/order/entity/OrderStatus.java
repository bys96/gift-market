package com.giftmarket.order.entity;

public enum OrderStatus {

    ORDERED,
    PENDING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    PAYMENT_EXPIRED,
    CANCELLED
}
