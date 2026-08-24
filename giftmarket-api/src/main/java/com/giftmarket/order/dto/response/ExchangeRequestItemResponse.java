package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.ExchangeInspectionResult;
import com.giftmarket.order.entity.ExchangeRequestItem;

public record ExchangeRequestItemResponse(
        Long orderItemId,
        String originalProductName,
        String originalOptionSnapshot,
        int quantity,
        String targetProductName,
        String targetOptionSnapshot,
        long targetUnitPrice,
        ExchangeInspectionResult inspectionResult,
        int restockedQuantity
) {
    public static ExchangeRequestItemResponse from(ExchangeRequestItem item) {
        return new ExchangeRequestItemResponse(
                item.getOrderItem().getId(), item.getOrderItem().getProductName(),
                item.getOrderItem().getOptionSnapshot(), item.getQuantity(),
                item.getTargetProductName(), item.getTargetOptionSnapshot(), item.getTargetUnitPrice(),
                item.getInspectionResult(), item.getRestockedQuantity()
        );
    }
}
