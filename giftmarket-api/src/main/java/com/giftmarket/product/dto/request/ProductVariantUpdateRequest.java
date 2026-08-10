package com.giftmarket.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductVariantUpdateRequest(

        @NotNull(message = "SKU 목록은 null일 수 없습니다.")
        @Size(
                max = 500,
                message = "한 상품에는 최대 500개의 SKU를 등록할 수 있습니다."
        )
        List<@Valid ProductVariantRequest> variants

) {

    public List<ProductVariantRequest> normalizedVariants() {
        return variants == null
                ? List.of()
                : List.copyOf(variants);
    }
}