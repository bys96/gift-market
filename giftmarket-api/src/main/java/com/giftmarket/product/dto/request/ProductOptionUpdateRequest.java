package com.giftmarket.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductOptionUpdateRequest(

        @NotNull(message = "상품 옵션 목록은 null일 수 없습니다.")
        @Size(
                max = 10,
                message = "상품 옵션은 최대 10개까지 등록할 수 있습니다."
        )
        List<@Valid ProductOptionGroupRequest> optionGroups

) {

    public List<ProductOptionGroupRequest> normalizedOptionGroups() {
        return optionGroups == null
                ? List.of()
                : List.copyOf(optionGroups);
    }
}