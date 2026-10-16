package com.giftmarket.product.dto.request;

import jakarta.validation.constraints.NotNull;

public record GiftOccasionActiveUpdateRequest(
        @NotNull(message = "활성 상태를 선택해주세요.") Boolean active
) {
}
