package com.giftmarket.notification.event;

public record NewOrderCreatedEvent(
        Long sellerUserId,
        Long sellerOrderId,
        String orderNumber
) {
}
