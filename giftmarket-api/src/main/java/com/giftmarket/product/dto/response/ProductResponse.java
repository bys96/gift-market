package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductImage;
import com.giftmarket.product.entity.ProductStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProductResponse {

    private Long id;

    private Long sellerId;

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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static ProductResponse from(
            Product product,
            List<ProductImage> productImages
    ) {
        List<String> galleryImageKeys = productImages.stream()
                .map(ProductImage::getObjectKey)
                .toList();

        return ProductResponse.builder()
                .id(product.getId())
                .sellerId(product.getSeller().getId())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .brandName(product.getBrandName())
                .summary(product.getSummary())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .status(product.getStatus())
                .representativeImageKey(
                        product.getRepresentativeImageKey()
                )
                .galleryImageKeys(galleryImageKeys)
                .freeShipping(product.isFreeShipping())
                .shippingFee(product.getShippingFee())
                .shippingPreparationDays(
                        product.getShippingPreparationDays()
                )
                .returnShippingFee(
                        product.getReturnShippingFee()
                )
                .exchangeShippingFee(
                        product.getExchangeShippingFee()
                )
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}