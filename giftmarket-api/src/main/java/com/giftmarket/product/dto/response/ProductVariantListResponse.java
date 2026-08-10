package com.giftmarket.product.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProductVariantListResponse {

    private Long productId;

    private List<ProductVariantResponse> variants;

    public static ProductVariantListResponse of(
            Long productId,
            List<ProductVariantResponse> variants
    ) {
        return ProductVariantListResponse.builder()
                .productId(productId)
                .variants(variants)
                .build();
    }
}