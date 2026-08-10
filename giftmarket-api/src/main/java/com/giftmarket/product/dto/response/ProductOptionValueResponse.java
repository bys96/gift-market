package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.ProductOptionValue;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductOptionValueResponse {

    private Long id;

    private String value;

    private Integer sortOrder;

    public static ProductOptionValueResponse from(
            ProductOptionValue optionValue
    ) {
        return ProductOptionValueResponse.builder()
                .id(optionValue.getId())
                .value(optionValue.getValue())
                .sortOrder(optionValue.getSortOrder())
                .build();
    }
}