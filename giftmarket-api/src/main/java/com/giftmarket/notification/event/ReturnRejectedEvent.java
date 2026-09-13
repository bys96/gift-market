package com.giftmarket.notification.event;

public record ReturnRejectedEvent(
        Long buyerUserId,
        Long returnRequestId,
        Long orderId
) {
}
