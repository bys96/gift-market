package com.giftmarket.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerOrderCancellationRejectRequest(
        @NotBlank @Size(max = 500)
        String reason
) {
}
