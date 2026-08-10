package com.giftmarket.cart.dto.response;

import com.giftmarket.product.entity.ProductOptionValue;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartItemOptionResponse {

    private Long optionGroupId;

    private String optionGroupName;

    private Long optionValueId;

    private String optionValue;

    public static CartItemOptionResponse from(
            ProductOptionValue optionValue
    ) {
        return CartItemOptionResponse.builder()
                .optionGroupId(
                        optionValue.getOptionGroup().getId()
                )
                .optionGroupName(
                        optionValue.getOptionGroup().getName()
                )
                .optionValueId(
                        optionValue.getId()
                )
                .optionValue(
                        optionValue.getValue()
                )
                .build();
    }
}