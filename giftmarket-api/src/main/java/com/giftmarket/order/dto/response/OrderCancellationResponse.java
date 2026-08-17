package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderCancellationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderCancellationResponse(
        Long cancellationId,
        Long orderId,
        Long sellerOrderId,
        OrderCancellationStatus status,
        String reason,
        LocalDateTime requestedAt,
        List<OrderCancellationItemResponse> items
) {
    public static OrderCancellationResponse from(
            OrderCancellation cancellation,
            List<OrderCancellationItem> items
    ) {
        return new OrderCancellationResponse(
                cancellation.getId(),
                cancellation.getOrder().getId(),
                cancellation.getSellerOrder().getId(),
                cancellation.getStatus(),
                cancellation.getReason(),
                cancellation.getRequestedAt(),
                items.stream()
                        .map(OrderCancellationItemResponse::from)
                        .toList()
        );
    }
}
