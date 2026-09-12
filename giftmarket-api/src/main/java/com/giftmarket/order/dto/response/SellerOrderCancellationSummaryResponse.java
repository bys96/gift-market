package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderCancellationRequesterType;

import java.time.LocalDateTime;

public record SellerOrderCancellationSummaryResponse(
        Long cancellationId,
        OrderCancellationStatus status,
        OrderCancellationRequesterType requesterType,
        LocalDateTime requestedAt
) {
    public static SellerOrderCancellationSummaryResponse from(
            OrderCancellation cancellation
    ) {
        return new SellerOrderCancellationSummaryResponse(
                cancellation.getId(),
                cancellation.getStatus(),
                cancellation.getRequesterType(),
                cancellation.getRequestedAt()
        );
    }
}
