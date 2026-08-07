package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductSummaryResponse {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String name;

    private String brandName;

    private Long price;

    private ProductStatus status;

    private String representativeImageKey;

    private boolean freeShipping;

    private Long shippingFee;

    public static ProductSummaryResponse from(Product product) {
        return ProductSummaryResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .brandName(product.getBrandName())
                .price(product.getPrice())
                .status(product.getStatus())
                .representativeImageKey(
                        product.getRepresentativeImageKey()
                )
                .freeShipping(product.isFreeShipping())
                .shippingFee(product.getShippingFee())
                .build();
    }
}