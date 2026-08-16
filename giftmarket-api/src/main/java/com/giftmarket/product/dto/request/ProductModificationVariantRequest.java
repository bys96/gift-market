package com.giftmarket.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductModificationVariantRequest(

        /*
         * 기존 SKU 수정: 기존 Variant ID
         * 신규 SKU 추가: null
         */
        Long id,

        @NotBlank(message = "SKU 코드를 입력해주세요.")
        @Size(
                max = 100,
                message = "SKU 코드는 100자 이하입니다."
        )
        String skuCode,

        @NotEmpty(message = "옵션 조합을 확인해주세요.")
        @Size(
                max = 10,
                message = "하나의 SKU에는 최대 10개의 옵션을 조합할 수 있습니다."
        )
        List<@Valid ProductOptionReferenceRequest> options,

        @NotNull(message = "옵션 추가 금액을 입력해주세요.")
        Long additionalPrice,

        @NotNull(message = "옵션 재고 수량을 입력해주세요.")
        @PositiveOrZero(message = "옵션 재고 수량은 0개 이상이어야 합니다.")
        Integer stockQuantity,

        @NotNull(message = "SKU 활성 여부를 선택해주세요.")
        Boolean active

) {

    public List<ProductOptionReferenceRequest> normalizedOptions() {
        return options == null
                ? List.of()
                : List.copyOf(options);
    }
}
