package com.giftmarket.product.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductOptionReferenceRequest(

        @NotNull(message = "옵션 순서를 확인해주세요.")
        @PositiveOrZero(message = "옵션 순서는 0 이상이어야 합니다.")
        Integer optionGroupSortOrder,

        @NotNull(message = "옵션 값 순서를 확인해주세요.")
        @PositiveOrZero(message = "옵션 값 순서는 0 이상이어야 합니다.")
        Integer optionValueSortOrder

) {
}