package com.giftmarket.product.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProductOptionResponse {

    private Long productId;

    @Builder.Default
    private List<ProductOptionGroupResponse> archivedOptionGroups = List.of();

    private List<ProductOptionGroupResponse> optionGroups;

    public static ProductOptionResponse of(
            Long productId,
            List<ProductOptionGroupResponse> optionGroups
    ) {
        return ProductOptionResponse.builder()
                .productId(productId)
                .optionGroups(optionGroups)
                .build();
    }
}