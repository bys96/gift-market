package com.giftmarket.order.repository;

public interface PendingCancellationQuantityProjection {
    Long getOrderItemId();

    Long getPendingQuantity();
}
