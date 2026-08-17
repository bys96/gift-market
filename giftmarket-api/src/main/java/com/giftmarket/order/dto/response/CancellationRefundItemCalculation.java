package com.giftmarket.order.dto.response;

public record CancellationRefundItemCalculation(
        Long orderItemId,
        int requestedQuantity,
        long unitRefundAmount,
        long refundAmount,
        int remainingQuantityAfterCancellation
) {
}
