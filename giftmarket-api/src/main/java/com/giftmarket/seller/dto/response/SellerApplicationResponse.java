package com.giftmarket.seller.dto.response;

import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;

import java.time.LocalDateTime;

public record SellerApplicationResponse(

        Long id,
        Long userId,
        String userName,
        String userEmail,
        String storeName,
        String introduction,
        SellerApplicationStatus status,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt

) {

    public static SellerApplicationResponse from(
            SellerApplication application
    ) {
        return new SellerApplicationResponse(
                application.getId(),
                application.getUser().getId(),
                application.getUser().getName(),
                application.getUser().getEmail(),
                application.getStoreName(),
                application.getIntroduction(),
                application.getStatus(),
                application.getRejectionReason(),
                application.getCreatedAt(),
                application.getReviewedAt()
        );
    }
}