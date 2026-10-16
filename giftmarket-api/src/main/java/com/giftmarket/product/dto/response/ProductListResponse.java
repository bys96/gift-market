package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

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

    private boolean adminHidden;

    private String adminHiddenReason;

    private LocalDateTime adminHiddenAt;

    private String representativeImageKey;

    private List<GiftOccasionResponse> giftOccasions;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static ProductListResponse from(Product product) {
        return from(product, List.of());
    }

    public static ProductListResponse from(
            Product product,
            List<GiftOccasionResponse> giftOccasions
    ) {
        return ProductListResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .brandName(product.getBrandName())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .status(product.getStatus())
                .adminHidden(product.isAdminHidden())
                .adminHiddenReason(product.getAdminHiddenReason())
                .adminHiddenAt(product.getAdminHiddenAt())
                .representativeImageKey(
                        product.getRepresentativeImageKey()
                )
                .giftOccasions(List.copyOf(giftOccasions))
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
