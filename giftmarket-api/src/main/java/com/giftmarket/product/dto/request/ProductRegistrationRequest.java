package com.giftmarket.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductRegistrationRequest(

        /*
         * 상품 기본정보.
         *
         * startSale은 서버에서 강제로 false로 바꿔
         * Product/Option/Variant 저장이 끝나기 전에
         * 판매 상품으로 노출되지 않도록 합니다.
         */
        @NotNull(message = "상품 정보를 확인해주세요.")
        @Valid
        ProductCreateRequest product,

        /*
         * 옵션 미사용 상품이면
         * optionGroups = []
         */
        @NotNull(message = "상품 옵션 정보를 확인해주세요.")
        @Valid
        ProductOptionUpdateRequest options,

        /*
         * 옵션 미사용 상품이면
         * variants = []
         */
        @NotNull(message = "SKU 정보를 확인해주세요.")
        @Size(
                max = 500,
                message = "한 상품에는 최대 500개의 SKU를 등록할 수 있습니다."
        )
        List<@Valid ProductRegistrationVariantRequest> variants,

        /*
         * 임시저장을 거쳐 등록하는 경우 Draft ID.
         * 바로 등록하는 경우 null.
         */
        Long draftId

) {

    public List<ProductRegistrationVariantRequest> normalizedVariants() {
        return variants == null
                ? List.of()
                : List.copyOf(variants);
    }
}