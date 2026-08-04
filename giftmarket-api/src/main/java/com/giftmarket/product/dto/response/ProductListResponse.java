package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProductListResponse {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String name;

    private String brandName;

    private Long price;

    private Integer stockQuantity;

    private ProductStatus status;

    private String representativeImageKey;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static ProductListResponse from(Product product) {
        return ProductListResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .brandName(product.getBrandName())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .status(product.getStatus())
                .representativeImageKey(
                        product.getRepresentativeImageKey()
                )
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}