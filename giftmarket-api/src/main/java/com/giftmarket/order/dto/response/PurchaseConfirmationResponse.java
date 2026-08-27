package com.giftmarket.order.dto.response;

public record PurchaseConfirmationResponse(
        Long orderItemId,
        int confirmedQuantity,
        int confirmableQuantity
) {
}
