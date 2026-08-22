package com.giftmarket.order.dto.response;

public record ReturnRequestImageResponse(
        Long imageId,
        String url,
        int sortOrder
) {
}
