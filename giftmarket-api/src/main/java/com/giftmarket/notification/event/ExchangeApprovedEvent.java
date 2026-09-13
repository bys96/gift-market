package com.giftmarket.notification.event;

public record ExchangeApprovedEvent(
        Long buyerUserId,
        Long exchangeRequestId,
        Long orderId
) {
}
