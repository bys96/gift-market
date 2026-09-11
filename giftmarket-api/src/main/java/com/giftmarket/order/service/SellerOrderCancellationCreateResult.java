package com.giftmarket.order.service;

import com.giftmarket.order.dto.response.OrderCancellationResponse;

public record SellerOrderCancellationCreateResult(
        OrderCancellationResponse cancellation,
        boolean newlyCreated
) {
}
