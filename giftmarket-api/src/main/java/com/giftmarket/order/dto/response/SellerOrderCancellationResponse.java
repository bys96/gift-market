package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderCancellationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record SellerOrderCancellationResponse(
        Long cancellationId,
        String orderNumber,
        Long sellerOrderId,
        OrderCancellationStatus status,
        String reason,
        String rejectedReason,
        LocalDateTime requestedAt,
        LocalDateTime processingAt,
        LocalDateTime rejectedAt,
        String recipientName,
        List<SellerOrderCancellationItemResponse> items
) {
    public static SellerOrderCancellationResponse from(
            OrderCancellation cancellation,
            List<OrderCancellationItem> items
    ) {
        return new SellerOrderCancellationResponse(
                cancellation.getId(),
                cancellation.getOrder().getOrderNumber(),
                cancellation.getSellerOrder().getId(),
                cancellation.getStatus(),
                cancellation.getReason(),
                cancellation.getRejectedReason(),
                cancellation.getRequestedAt(),
                cancellation.getProcessingAt(),
                cancellation.getRejectedAt(),
                cancellation.getOrder().getRecipientName(),
                items.stream()
                        .map(SellerOrderCancellationItemResponse::from)
                        .toList()
        );
    }
}
