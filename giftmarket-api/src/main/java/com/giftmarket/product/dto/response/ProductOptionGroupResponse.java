package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.ProductOptionGroup;
import com.giftmarket.product.entity.ProductOptionValue;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProductOptionGroupResponse {

    private Long id;

    private String name;

    private Integer sortOrder;

    private List<ProductOptionValueResponse> values;

    public static ProductOptionGroupResponse from(
            ProductOptionGroup optionGroup,
            List<ProductOptionValue> optionValues
    ) {
        return ProductOptionGroupResponse.builder()
                .id(optionGroup.getId())
                .name(optionGroup.getName())
                .sortOrder(optionGroup.getSortOrder())
                .values(
                        optionValues.stream()
                                .map(ProductOptionValueResponse::from)
                                .toList()
                )
                .build();
    }
}