package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderCancellationItem;

public record OrderCancellationItemResponse(
        Long orderItemId,
        int requestedQuantity
) {
    public static OrderCancellationItemResponse from(OrderCancellationItem item) {
        return new OrderCancellationItemResponse(
                item.getOrderItem().getId(),
                item.getQuantity()
        );
    }
}
