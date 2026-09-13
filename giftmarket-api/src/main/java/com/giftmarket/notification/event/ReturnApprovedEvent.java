package com.giftmarket.notification.event;

public record ReturnApprovedEvent(
        Long buyerUserId,
        Long returnRequestId,
        Long orderId
) {
}
