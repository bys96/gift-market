package com.giftmarket.notification.event;

public record ExchangeReshippedEvent(
        Long buyerUserId,
        Long exchangeRequestId,
        Long orderId
) {
}
