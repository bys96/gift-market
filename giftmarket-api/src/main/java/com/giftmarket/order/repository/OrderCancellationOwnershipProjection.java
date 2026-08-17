package com.giftmarket.order.repository;

public interface OrderCancellationOwnershipProjection {
    Long getOrderId();

    Long getSellerOrderId();
}
