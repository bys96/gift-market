package com.giftmarket.cart.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CartItemsCreateRequest(
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid CartItemCreateRequest> items
) {
}
