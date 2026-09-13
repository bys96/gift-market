package com.giftmarket.notification.event;

public record OrderShippedEvent(
        Long buyerUserId,
        Long orderId,
        Long sellerOrderId,
        String storeName
) {
}
