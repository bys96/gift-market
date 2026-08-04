package com.giftmarket.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(

        Long parentId,

        @NotBlank(message = "카테고리명을 입력해주세요.")
        @Size(
                max = 100,
                message = "카테고리명은 100자 이하입니다."
        )
        String name,

        @NotNull(message = "정렬 순서를 입력해주세요.")
        @PositiveOrZero(message = "정렬 순서는 0 이상이어야 합니다.")
        Integer sortOrder

) {
}