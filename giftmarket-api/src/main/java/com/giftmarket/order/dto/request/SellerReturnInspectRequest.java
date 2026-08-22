package com.giftmarket.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SellerReturnInspectRequest(
        @NotNull @Size(min = 1, max = 100)
        List<@Valid SellerReturnInspectionItemRequest> items
) {
}
