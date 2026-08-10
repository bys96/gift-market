package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.ProductOptionValue;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.entity.ProductVariantOptionValue;
import lombok.Builder;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;

@Getter
@Builder
public class ProductDetailVariantResponse {

    private Long id;

    private List<Long> optionValueIds;

    private Long additionalPrice;

    private Long price;

    private Integer stockQuantity;

    private boolean available;

    public static ProductDetailVariantResponse from(
            ProductVariant variant,
            List<ProductVariantOptionValue> variantOptionValues,
            Long productPrice
    ) {
        List<Long> optionValueIds = variantOptionValues.stream()
                .map(ProductVariantOptionValue::getOptionValue)
                .sorted(
                        Comparator.comparing(
                                (ProductOptionValue value) ->
                                        value.getOptionGroup()
                                                .getSortOrder()
                        ).thenComparing(
                                ProductOptionValue::getSortOrder
                        )
                )
                .map(ProductOptionValue::getId)
                .toList();

        return ProductDetailVariantResponse.builder()
                .id(variant.getId())
                .optionValueIds(optionValueIds)
                .additionalPrice(
                        variant.getAdditionalPrice()
                )
                .price(
                        productPrice
                                + variant.getAdditionalPrice()
                )
                .stockQuantity(
                        variant.getStockQuantity()
                )
                .available(
                        variant.isActive()
                                && variant.getStockQuantity() > 0
                )
                .build();
    }
}