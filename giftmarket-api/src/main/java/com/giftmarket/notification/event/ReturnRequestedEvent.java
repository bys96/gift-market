package com.giftmarket.notification.event;

public record ReturnRequestedEvent(
        Long sellerUserId,
        Long returnRequestId
) {
}
