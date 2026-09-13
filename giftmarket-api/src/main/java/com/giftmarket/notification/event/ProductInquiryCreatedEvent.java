package com.giftmarket.notification.event;

public record ProductInquiryCreatedEvent(
        Long sellerUserId,
        Long inquiryId,
        String productName
) {
}
