package com.giftmarket.order.dto.request;

import com.giftmarket.order.entity.ExchangeInspectionResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SellerExchangeInspectionItemRequest(
        @NotNull @Positive Long orderItemId,
        @NotNull ExchangeInspectionResult inspectionResult
) { }
