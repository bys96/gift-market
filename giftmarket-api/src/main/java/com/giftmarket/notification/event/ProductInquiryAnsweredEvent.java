package com.giftmarket.notification.event;

public record ProductInquiryAnsweredEvent(
        Long buyerUserId,
        Long inquiryId,
        Long productId,
        String productName
) {
}
