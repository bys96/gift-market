package com.giftmarket.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCancellationCreateRequest(
        @NotBlank
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "취소 요청 키는 UUID 형식이어야 합니다."
        )
        String clientRequestKey,

        @NotNull @Positive
        Long sellerOrderId,

        @NotBlank @Size(max = 500)
        String reason,

        @NotNull @Size(min = 1, max = 100)
        List<@Valid OrderCancellationItemRequest> items
) {
}
