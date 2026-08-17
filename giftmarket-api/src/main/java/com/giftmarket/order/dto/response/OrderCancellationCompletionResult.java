package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.SellerOrderStatus;

public record OrderCancellationCompletionResult(
        Long cancellationId,
        OrderCancellationStatus status,
        SellerOrderStatus sellerOrderStatus
) {
}
