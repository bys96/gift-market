package com.giftmarket.notification.event;

public record ExchangeRejectedEvent(
        Long buyerUserId,
        Long exchangeRequestId,
        Long orderId
) {
}
