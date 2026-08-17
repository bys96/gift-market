package com.giftmarket.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderCancellationItemRequest(
        @NotNull @Positive Long orderItemId,
        @NotNull @Positive Integer quantity
) {
}
