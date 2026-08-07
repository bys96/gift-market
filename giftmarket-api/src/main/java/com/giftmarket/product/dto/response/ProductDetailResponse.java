package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductImage;
import com.giftmarket.product.entity.ProductStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

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

    public static ProductDetailResponse from(
            Product product,
            List<ProductImage> productImages
    ) {
        return ProductDetailResponse.builder()
                .id(product.getId())
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
                .galleryImageKeys(
                        productImages.stream()
                                .map(ProductImage::getObjectKey)
                                .toList()
                )
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
                .sellerId(product.getSeller().getId())
                .storeName(product.getSeller().getStoreName())
                .sellerIntroduction(
                        product.getSeller().getIntroduction()
                )
                .build();
    }
}