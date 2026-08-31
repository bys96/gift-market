package com.giftmarket.admin.dto.response;

import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;

import java.time.LocalDateTime;

public record AdminSellerSummaryResponse(
        Long sellerId,
        String storeName,
        SellerStatus status,
        LocalDateTime createdAt,
        Long userId,
        String userName,
        String userEmail,
        long onSaleProductCount
) {
    public static AdminSellerSummaryResponse from(Seller seller, long onSaleProductCount) {
        return new AdminSellerSummaryResponse(
                seller.getId(),
                seller.getStoreName(),
                seller.getStatus(),
                seller.getCreatedAt(),
                seller.getUser().getId(),
                seller.getUser().getName(),
                seller.getUser().getEmail(),
                onSaleProductCount
        );
    }
}
