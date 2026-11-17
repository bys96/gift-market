package com.giftmarket.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DirectOrderItemRequest(
        @Positive Long variantId,
        @NotNull @Positive Integer quantity
) {
}
