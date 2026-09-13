package com.giftmarket.notification.event;

public record ExchangeRequestedEvent(
        Long sellerUserId,
        Long exchangeRequestId
) {
}
