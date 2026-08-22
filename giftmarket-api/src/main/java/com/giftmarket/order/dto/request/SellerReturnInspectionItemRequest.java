package com.giftmarket.order.dto.request;

import com.giftmarket.order.entity.ReturnInspectionResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SellerReturnInspectionItemRequest(
        @NotNull @Positive Long orderItemId,
        @NotNull ReturnInspectionResult inspectionResult
) {
}
