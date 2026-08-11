package com.giftmarket.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductModificationRequest(

        @NotNull(message = "상품 정보를 확인해주세요.")
        @Valid
        ProductUpdateRequest product,

        @NotNull(message = "상품 옵션 정보를 확인해주세요.")
        @Valid
        ProductOptionUpdateRequest options,

        @NotNull(message = "SKU 정보를 확인해주세요.")
        @Size(
                max = 500,
                message = "한 상품에는 최대 500개의 SKU를 등록할 수 있습니다."
        )
        List<@Valid ProductModificationVariantRequest> variants,

        /*
         * 수정 중 임시저장이 존재하면 Draft ID.
         * 바로 수정 완료한 경우 null.
         */
        Long draftId

) {

    public List<ProductModificationVariantRequest> normalizedVariants() {
        return variants == null
                ? List.of()
                : List.copyOf(variants);
    }
}