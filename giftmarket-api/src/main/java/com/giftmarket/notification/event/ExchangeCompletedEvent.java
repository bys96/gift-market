package com.giftmarket.notification.event;

public record ExchangeCompletedEvent(
        Long buyerUserId,
        Long exchangeRequestId,
        Long orderId
) {
}
