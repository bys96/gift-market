package com.giftmarket.seller.dto.response;

import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;

import java.time.LocalDateTime;

public record SellerApplicationResponse(

        Long id,
        String storeName,
        String introduction,
        SellerApplicationStatus status,
        String rejectionReason,
        LocalDateTime reviewedAt

) {

    public static SellerApplicationResponse from(SellerApplication application) {
        return new SellerApplicationResponse(
                application.getId(),
                application.getStoreName(),
                application.getIntroduction(),
                application.getStatus(),
                application.getRejectionReason(),
                application.getReviewedAt()
        );
    }
}