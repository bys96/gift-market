package com.giftmarket.admin.dto.response;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.seller.entity.SellerApplicationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
        ActionCenter actionCenter,
        Summary summary,
        List<RecentOrder> recentOrders,
        List<RecentSellerApplication> recentSellerApplications
) {
    public record ActionCenter(
            long pendingSellerApplications,
            long pendingCancellations,
            long pendingReturns,
            long pendingExchanges
    ) {
    }

    public record Summary(
            long totalUsers,
            long activeSellers,
            long sellingProducts,
            long totalOrders
    ) {
    }

    public record RecentOrder(
            Long id,
            String orderNumber,
            OrderStatus status,
            long totalAmount,
            LocalDateTime orderedAt,
            LocalDateTime createdAt
    ) {
    }

    public record RecentSellerApplication(
            Long id,
            String storeName,
            String applicantName,
            SellerApplicationStatus status,
            LocalDateTime createdAt
    ) {
    }
}
