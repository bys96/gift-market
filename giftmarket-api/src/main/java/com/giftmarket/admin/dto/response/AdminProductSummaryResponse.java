package com.giftmarket.admin.dto.response;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;

import java.time.LocalDateTime;

public record AdminProductSummaryResponse(
        Long productId,
        String productName,
        String representativeImageKey,
        ProductStatus status,
        boolean adminHidden,
        boolean deleted,
        LocalDateTime createdAt,
        Long sellerId,
        String storeName,
        long price,
        long availableStock
) {
    public static AdminProductSummaryResponse from(Product product, long availableStock) {
        return new AdminProductSummaryResponse(
                product.getId(), product.getName(), product.getRepresentativeImageKey(),
                product.getStatus(), product.isAdminHidden(), product.isDeleted(), product.getCreatedAt(),
                product.getSeller().getId(), product.getSeller().getStoreName(),
                product.getPrice(), availableStock
        );
    }
}
