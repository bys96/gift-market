package com.giftmarket.product.dto.request;
import com.giftmarket.global.util.MoneyLimits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductVariantRequest(

        Long id,

        @NotBlank(message = "SKU 코드를 입력해주세요.")
        @Size(
                max = 100,
                message = "SKU 코드는 100자 이하입니다."
        )
        @com.fasterxml.jackson.annotation.JsonProperty("sellerSkuCode")
        String sellerSkuCode,

        @NotEmpty(message = "옵션 조합을 선택해주세요.")
        @Size(
                max = 10,
                message = "하나의 SKU에는 최대 10개의 옵션 값을 조합할 수 있습니다."
        )
        List<@NotNull(message = "옵션 값을 선택해주세요.") Long> optionValueIds,

        @NotNull(message = "옵션 추가 금액을 입력해주세요.")
        @Min(value = -MoneyLimits.MAX_AMOUNT, message = "옵션 추가금은 -1,000만원 이상이어야 합니다.")
        @Max(value = MoneyLimits.MAX_AMOUNT, message = "옵션 추가금은 1,000만원 이하여야 합니다.")
        Long additionalPrice,

        @PositiveOrZero(message = "옵션 재고 수량은 0개 이상이어야 합니다.")
        Integer stockQuantity,

        @NotNull(message = "SKU 활성 여부를 선택해주세요.")
        Boolean active,

        Long expectedVersion

) {

    public ProductVariantRequest {
        if (sellerSkuCode == null) {
            throw new com.giftmarket.product.exception.ProductConflictException(
                    "이전 형식의 상품 요청입니다. 최신 화면에서 sellerSkuCode를 확인한 후 다시 저장해주세요.");
        }
    }
    public ProductVariantRequest(Long id, String sellerSkuCode, List<Long> optionValueIds,
            Long additionalPrice, Integer stockQuantity, Boolean active) {
        this(id, sellerSkuCode, optionValueIds, additionalPrice, stockQuantity, active, null);
    }

    public List<Long> normalizedOptionValueIds() {
        return optionValueIds == null
                ? List.of()
                : List.copyOf(optionValueIds);
    }
}
