package com.giftmarket.product.dto.response;

import lombok.Builder;

@Builder
public record ProductRegistrationResponse(

        Long productId,

        ProductResponse product

) {

    public static ProductRegistrationResponse from(
            ProductResponse product
    ) {
        return ProductRegistrationResponse.builder()
                .productId(product.getId())
                .product(product)
                .build();
    }
}