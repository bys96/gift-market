package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.ProductOptionValue;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductVariantOptionValueResponse {

    private Long optionGroupId;

    private String optionGroupName;

    private Integer optionGroupSortOrder;

    private Long optionValueId;

    private String optionValue;

    private Integer optionValueSortOrder;

    public static ProductVariantOptionValueResponse from(
            ProductOptionValue optionValue
    ) {
        return ProductVariantOptionValueResponse.builder()
                .optionGroupId(
                        optionValue.getOptionGroup().getId()
                )
                .optionGroupName(
                        optionValue.getOptionGroup().getName()
                )
                .optionGroupSortOrder(
                        optionValue.getOptionGroup().getSortOrder()
                )
                .optionValueId(optionValue.getId())
                .optionValue(optionValue.getValue())
                .optionValueSortOrder(
                        optionValue.getSortOrder()
                )
                .build();
    }
}