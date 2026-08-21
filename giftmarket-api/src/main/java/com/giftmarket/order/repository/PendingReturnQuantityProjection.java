package com.giftmarket.order.repository;

public interface PendingReturnQuantityProjection {

    Long getOrderItemId();

    Long getPendingQuantity();
}