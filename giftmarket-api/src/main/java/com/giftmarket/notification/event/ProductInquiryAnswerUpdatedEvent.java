package com.giftmarket.notification.event;

public record ProductInquiryAnswerUpdatedEvent(
        Long buyerUserId,
        Long inquiryId,
        Long productId,
        String productName
) {
}
