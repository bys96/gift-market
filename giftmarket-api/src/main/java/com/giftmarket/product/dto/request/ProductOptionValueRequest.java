package com.giftmarket.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductOptionValueRequest(

        Long id,

        @NotBlank(message = "옵션 값을 입력해주세요.")
        @Size(
                max = 100,
                message = "옵션 값은 100자 이하입니다."
        )
        String value,

        @NotNull(message = "옵션 값 순서를 입력해주세요.")
        @PositiveOrZero(message = "옵션 값 순서는 0 이상이어야 합니다.")
        Integer sortOrder

) {
}