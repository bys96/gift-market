package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.ReturnInspectionResult;
import com.giftmarket.order.entity.ReturnRequestItem;

public record ReturnRequestItemResponse(
        Long orderItemId,
        String productName,
        String optionSnapshot,
        int quantity,
        ReturnInspectionResult inspectionResult,
        int restockedQuantity
) {
    public static ReturnRequestItemResponse from(ReturnRequestItem item) {
        return new ReturnRequestItemResponse(
                item.getOrderItem().getId(),
                item.getOrderItem().getProductName(),
                item.getOrderItem().getOptionSnapshot(),
                item.getQuantity(),
                item.getInspectionResult(),
                item.getRestockedQuantity()
        );
    }
}
