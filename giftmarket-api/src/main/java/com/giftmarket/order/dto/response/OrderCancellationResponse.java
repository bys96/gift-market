package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderCancellationRequesterType;

import java.time.LocalDateTime;
import java.util.List;

public record OrderCancellationResponse(
        Long cancellationId,
        Long orderId,
        Long sellerOrderId,
        OrderCancellationStatus status,
        OrderCancellationRequesterType requesterType,
        String reason,
        LocalDateTime requestedAt,
        LocalDateTime processingAt,
        LocalDateTime completedAt,
        LocalDateTime rejectedAt,
        String rejectedReason,
        LocalDateTime failedAt,
        List<OrderCancellationItemResponse> items
) {
    public OrderCancellationResponse(
            Long cancellationId,
            Long orderId,
            Long sellerOrderId,
            OrderCancellationStatus status,
            String reason,
            LocalDateTime requestedAt,
            LocalDateTime processingAt,
            LocalDateTime completedAt,
            LocalDateTime rejectedAt,
            String rejectedReason,
            LocalDateTime failedAt,
            List<OrderCancellationItemResponse> items
    ) {
        this(
                cancellationId, orderId, sellerOrderId, status,
                OrderCancellationRequesterType.BUYER, reason,
                requestedAt, processingAt, completedAt, rejectedAt,
                rejectedReason, failedAt, items
        );
    }

    public static OrderCancellationResponse from(
            OrderCancellation cancellation,
            List<OrderCancellationItem> items
    ) {
        return new OrderCancellationResponse(
                cancellation.getId(),
                cancellation.getOrder().getId(),
                cancellation.getSellerOrder().getId(),
                cancellation.getStatus(),
                cancellation.getRequesterType(),
                cancellation.getReason(),
                cancellation.getRequestedAt(),
                cancellation.getProcessingAt(),
                cancellation.getCompletedAt(),
                cancellation.getRejectedAt(),
                cancellation.getRejectedReason(),
                cancellation.getFailedAt(),
                items.stream()
                        .map(OrderCancellationItemResponse::from)
                        .toList()
        );
    }
}
