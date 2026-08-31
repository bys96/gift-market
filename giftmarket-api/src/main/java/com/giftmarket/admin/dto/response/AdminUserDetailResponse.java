package com.giftmarket.admin.dto.response;

import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.entity.UserStatus;

import java.time.LocalDateTime;

public record AdminUserDetailResponse(
        Long id,
        String email,
        String name,
        UserRole role,
        AuthProvider provider,
        UserStatus status,
        String profileImageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        SellerInfo seller,
        SellerApplicationInfo latestSellerApplication,
        ActivitySummary activity
) {
    public static AdminUserDetailResponse from(
            User user,
            Seller seller,
            SellerApplication application,
            long orderCount,
            long reviewCount,
            long inquiryCount
    ) {
        return new AdminUserDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getProvider(),
                user.getStatus(),
                user.getProfileImageUrl(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                SellerInfo.from(seller),
                SellerApplicationInfo.from(application),
                new ActivitySummary(orderCount, reviewCount, inquiryCount)
        );
    }

    public record SellerInfo(
            Long sellerId,
            String storeName,
            SellerStatus status,
            LocalDateTime createdAt
    ) {
        private static SellerInfo from(Seller seller) {
            return seller == null ? null : new SellerInfo(
                    seller.getId(), seller.getStoreName(), seller.getStatus(), seller.getCreatedAt()
            );
        }
    }

    public record SellerApplicationInfo(
            Long applicationId,
            String storeName,
            SellerApplicationStatus status,
            LocalDateTime createdAt,
            LocalDateTime reviewedAt
    ) {
        private static SellerApplicationInfo from(SellerApplication application) {
            return application == null ? null : new SellerApplicationInfo(
                    application.getId(),
                    application.getStoreName(),
                    application.getStatus(),
                    application.getCreatedAt(),
                    application.getReviewedAt()
            );
        }
    }

    public record ActivitySummary(long orders, long reviews, long inquiries) {
    }
}
