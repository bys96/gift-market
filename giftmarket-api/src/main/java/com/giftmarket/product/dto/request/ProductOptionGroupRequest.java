package com.giftmarket.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductOptionGroupRequest(

        Long id,

        @NotBlank(message = "옵션명을 입력해주세요.")
        @Size(
                max = 50,
                message = "옵션명은 50자 이하입니다."
        )
        String name,

        @NotNull(message = "옵션 순서를 입력해주세요.")
        @PositiveOrZero(message = "옵션 순서는 0 이상이어야 합니다.")
        Integer sortOrder,

        @NotEmpty(message = "옵션 값을 1개 이상 등록해주세요.")
        @Size(
                max = 50,
                message = "하나의 옵션에는 최대 50개의 값을 등록할 수 있습니다."
        )
        List<@Valid ProductOptionValueRequest> values

) {

    public List<ProductOptionValueRequest> normalizedValues() {
        return values == null
                ? List.of()
                : List.copyOf(values);
    }
}