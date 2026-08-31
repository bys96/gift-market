package com.giftmarket.admin.dto.response;

import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.SellerOrderItemSummaryProjection;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.entity.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AdminSellerDetailResponse(
        Long sellerId,
        String storeName,
        String introduction,
        SellerStatus status,
        LocalDateTime approvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Owner owner,
        SellerApplicationInfo sellerApplication,
        ActivitySummary activity,
        List<RecentOrder> recentOrders
) {
    public static AdminSellerDetailResponse from(
            Seller seller,
            SellerApplication application,
            long totalProducts,
            long onSaleProducts,
            long totalOrders,
            List<RecentOrder> recentOrders
    ) {
        var user = seller.getUser();
        return new AdminSellerDetailResponse(
                seller.getId(),
                seller.getStoreName(),
                seller.getIntroduction(),
                seller.getStatus(),
                seller.getApprovedAt(),
                seller.getCreatedAt(),
                seller.getUpdatedAt(),
                new Owner(
                        user.getId(), user.getName(), user.getEmail(), user.getRole(),
                        user.getProvider(), user.getStatus(), user.getCreatedAt()
                ),
                SellerApplicationInfo.from(application),
                new ActivitySummary(totalProducts, onSaleProducts, totalOrders),
                recentOrders
        );
    }

    public record Owner(
            Long userId,
            String name,
            String email,
            UserRole role,
            AuthProvider provider,
            UserStatus status,
            LocalDateTime createdAt
    ) {
    }

    public record SellerApplicationInfo(
            Long applicationId,
            SellerApplicationStatus status,
            LocalDateTime appliedAt,
            LocalDateTime reviewedAt,
            Long reviewedBy
    ) {
        private static SellerApplicationInfo from(SellerApplication application) {
            return application == null ? null : new SellerApplicationInfo(
                    application.getId(),
                    application.getStatus(),
                    application.getCreatedAt(),
                    application.getReviewedAt(),
                    application.getReviewedBy()
            );
        }
    }

    public record ActivitySummary(long totalProducts, long onSaleProducts, long totalOrders) {
    }

    public record RecentOrder(
            Long sellerOrderId,
            Long orderId,
            String orderNumber,
            SellerOrderStatus status,
            long totalProductAmount,
            LocalDateTime orderedAt
    ) {
        public static RecentOrder from(
                SellerOrder sellerOrder,
                SellerOrderItemSummaryProjection summary
        ) {
            return new RecentOrder(
                    sellerOrder.getId(),
                    sellerOrder.getOrder().getId(),
                    sellerOrder.getOrder().getOrderNumber(),
                    sellerOrder.getStatus(),
                    summary.getTotalProductAmount(),
                    sellerOrder.getOrder().getOrderedAt()
            );
        }
    }
}
