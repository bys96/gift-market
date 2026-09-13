package com.giftmarket.notification.event;

public record SellerOrderCancelledEvent(
        Long buyerUserId,
        Long cancellationId,
        Long orderId
) {
}
