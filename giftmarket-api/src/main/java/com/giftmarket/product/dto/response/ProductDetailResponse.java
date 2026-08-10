package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductImage;
import com.giftmarket.product.entity.ProductOptionGroup;
import com.giftmarket.product.entity.ProductOptionValue;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.entity.ProductVariantOptionValue;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ProductDetailResponse {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String name;

    private String brandName;

    private String summary;

    private String description;

    private Long price;

    private Integer stockQuantity;

    private ProductStatus status;

    private String representativeImageKey;

    private List<String> galleryImageKeys;

    private boolean freeShipping;

    private Long shippingFee;

    private Integer shippingPreparationDays;

    private Long returnShippingFee;

    private Long exchangeShippingFee;

    private Long sellerId;

    private String storeName;

    private String sellerIntroduction;

    private boolean hasOptions;

    private List<ProductDetailOptionGroupResponse> optionGroups;

    private List<ProductDetailVariantResponse> variants;

    public static ProductDetailResponse from(
            Product product,
            List<ProductImage> productImages,
            List<ProductOptionGroup> optionGroups,
            List<ProductOptionValue> optionValues,
            List<ProductVariant> variants,
            List<ProductVariantOptionValue> variantOptionValues
    ) {
        boolean hasOptions = !optionGroups.isEmpty();

        Map<Long, List<ProductOptionValue>> valuesByGroupId =
                new HashMap<>();

        for (ProductOptionValue optionValue : optionValues) {
            valuesByGroupId
                    .computeIfAbsent(
                            optionValue.getOptionGroup().getId(),
                            key -> new ArrayList<>()
                    )
                    .add(optionValue);
        }

        List<ProductDetailOptionGroupResponse>
                optionGroupResponses =
                optionGroups.stream()
                        .map(optionGroup ->
                                ProductDetailOptionGroupResponse.from(
                                        optionGroup,
                                        valuesByGroupId.getOrDefault(
                                                optionGroup.getId(),
                                                List.of()
                                        )
                                )
                        )
                        .toList();

        Map<Long, List<ProductVariantOptionValue>>
                optionValuesByVariantId =
                new HashMap<>();

        for (ProductVariantOptionValue variantOptionValue
                : variantOptionValues) {

            optionValuesByVariantId
                    .computeIfAbsent(
                            variantOptionValue
                                    .getVariant()
                                    .getId(),
                            key -> new ArrayList<>()
                    )
                    .add(variantOptionValue);
        }

        List<ProductDetailVariantResponse>
                variantResponses =
                variants.stream()
                        .map(variant ->
                                ProductDetailVariantResponse.from(
                                        variant,
                                        optionValuesByVariantId
                                                .getOrDefault(
                                                        variant.getId(),
                                                        List.of()
                                                ),
                                        product.getPrice()
                                )
                        )
                        .toList();

        int availableStockQuantity =
                hasOptions
                        ? variants.stream()
                        .filter(ProductVariant::isActive)
                        .mapToInt(
                                ProductVariant::getStockQuantity
                        )
                        .sum()
                        : product.getStockQuantity();

        return ProductDetailResponse.builder()
                .id(product.getId())
                .categoryId(
                        product.getCategory().getId()
                )
                .categoryName(
                        product.getCategory().getName()
                )
                .name(product.getName())
                .brandName(product.getBrandName())
                .summary(product.getSummary())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(
                        availableStockQuantity
                )
                .status(product.getStatus())
                .representativeImageKey(
                        product.getRepresentativeImageKey()
                )
                .galleryImageKeys(
                        productImages.stream()
                                .map(ProductImage::getObjectKey)
                                .toList()
                )
                .freeShipping(
                        product.isFreeShipping()
                )
                .shippingFee(
                        product.getShippingFee()
                )
                .shippingPreparationDays(
                        product.getShippingPreparationDays()
                )
                .returnShippingFee(
                        product.getReturnShippingFee()
                )
                .exchangeShippingFee(
                        product.getExchangeShippingFee()
                )
                .sellerId(
                        product.getSeller().getId()
                )
                .storeName(
                        product.getSeller().getStoreName()
                )
                .sellerIntroduction(
                        product.getSeller()
                                .getIntroduction()
                )
                .hasOptions(hasOptions)
                .optionGroups(optionGroupResponses)
                .variants(variantResponses)
                .build();
    }
}