package com.giftmarket.notification.event;

public record BuyerCancellationCompletedEvent(
        Long buyerUserId,
        Long cancellationId,
        Long orderId
) {
}
