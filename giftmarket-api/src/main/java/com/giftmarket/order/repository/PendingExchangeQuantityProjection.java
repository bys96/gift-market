package com.giftmarket.order.repository;

public interface PendingExchangeQuantityProjection {
    Long getOrderItemId();
    Long getPendingQuantity();
}
