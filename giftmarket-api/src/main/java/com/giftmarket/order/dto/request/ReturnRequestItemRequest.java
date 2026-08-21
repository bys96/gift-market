package com.giftmarket.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReturnRequestItemRequest(
        @NotNull @Positive Long orderItemId,
        @NotNull @Positive Integer quantity
) {
}
