package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderItem;

public record SellerOrderCancellationItemResponse(
        Long orderItemId,
        String productName,
        String optionSnapshot,
        int orderedQuantity,
        int canceledQuantity,
        int requestedQuantity
) {
    public static SellerOrderCancellationItemResponse from(OrderCancellationItem cancellationItem) {
        OrderItem orderItem = cancellationItem.getOrderItem();
        return new SellerOrderCancellationItemResponse(
                orderItem.getId(),
                orderItem.getProductName(),
                orderItem.getOptionSnapshot(),
                orderItem.getQuantity(),
                orderItem.getCanceledQuantity(),
                cancellationItem.getQuantity()
        );
    }
}
