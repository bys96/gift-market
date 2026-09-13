package com.giftmarket.notification.event;

public record CancellationRequestedEvent(
        Long sellerUserId,
        Long cancellationId,
        String orderNumber
) {
}
