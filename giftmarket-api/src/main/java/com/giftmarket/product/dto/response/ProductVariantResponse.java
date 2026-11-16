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
public class ProductVariantResponse {

    private Long id;

    private Long version;

    private String skuCode;

    private String sellerSkuCode;

    private String combinationKey;

    private Long additionalPrice;

    private Integer stockQuantity;

    private boolean active;

    private boolean mappingValid;

    private List<ProductVariantOptionValueResponse> optionValues;

    public static ProductVariantResponse from(
            ProductVariant variant,
            List<ProductVariantOptionValue> variantOptionValues
    ) {
        List<ProductVariantOptionValueResponse> optionValues =
                variantOptionValues.stream()
                        .map(ProductVariantOptionValue::getOptionValue)
                        .sorted(
                                Comparator
                                        .comparing(
                                                (ProductOptionValue value) ->
                                                        value.getOptionGroup()
                                                                .getSortOrder()
                                        )
                                        .thenComparing(
                                                ProductOptionValue::getSortOrder
                                        )
                        )
                        .map(ProductVariantOptionValueResponse::from)
                        .toList();

        return ProductVariantResponse.builder()
                .id(variant.getId())
                .version(variant.getVersion())
                .skuCode(variant.getSkuCode())
                .sellerSkuCode(variant.getSellerSkuCode())
                .combinationKey(
                        variant.getCombinationKey()
                )
                .additionalPrice(
                        variant.getAdditionalPrice()
                )
                .stockQuantity(
                        variant.getStockQuantity()
                )
                .active(variant.isActive())
                .mappingValid(com.giftmarket.product.service.ProductVariantMapping.isValid(variant, variantOptionValues))
                .optionValues(optionValues)
                .build();
    }
}
