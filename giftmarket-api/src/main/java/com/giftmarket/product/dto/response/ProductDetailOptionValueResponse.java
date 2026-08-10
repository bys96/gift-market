package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.ProductOptionValue;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductDetailOptionValueResponse {

    private Long id;

    private String value;

    private Integer sortOrder;

    public static ProductDetailOptionValueResponse from(
            ProductOptionValue optionValue
    ) {
        return ProductDetailOptionValueResponse.builder()
                .id(optionValue.getId())
                .value(optionValue.getValue())
                .sortOrder(optionValue.getSortOrder())
                .build();
    }
}