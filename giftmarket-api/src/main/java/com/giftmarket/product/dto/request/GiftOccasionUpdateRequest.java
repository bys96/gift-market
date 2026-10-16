package com.giftmarket.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record GiftOccasionUpdateRequest(
        @NotBlank(message = "선물 상황 이름을 입력해주세요.")
        @Size(max = 100, message = "선물 상황 이름은 100자 이하여야 합니다.")
        String name,
        @NotNull(message = "노출 순서를 입력해주세요.")
        @PositiveOrZero(message = "노출 순서는 0 이상이어야 합니다.")
        Integer displayOrder
) {
}
