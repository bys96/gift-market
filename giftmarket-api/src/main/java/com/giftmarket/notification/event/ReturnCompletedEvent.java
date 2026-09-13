package com.giftmarket.notification.event;

public record ReturnCompletedEvent(
        Long buyerUserId,
        Long returnRequestId,
        Long orderId
) {
}
