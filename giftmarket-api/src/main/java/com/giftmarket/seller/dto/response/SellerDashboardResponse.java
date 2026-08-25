package com.giftmarket.seller.dto.response;

import com.giftmarket.order.entity.SellerOrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record SellerDashboardResponse(
        String storeName,
        ActionRequired actionRequired,
        ProductSummary products,
        List<RecentOrder> recentOrders
) {
    public record ActionRequired(
            long orders,
            long cancellations,
            ReturnActions returns,
            ExchangeActions exchanges
    ) {
    }

    public record ReturnActions(
            long total,
            long approvalRequired,
            long collectionRequired,
            long receivingRequired,
            long inspectionRequired
    ) {
    }

    public record ExchangeActions(
            long total,
            long approvalRequired,
            long collectionOrReceivingRequired,
            long inspectionRequired,
            long outboundRequired
    ) {
    }

    public record ProductSummary(
            long onSale,
            long soldOut
    ) {
    }

    public record RecentOrder(
            Long sellerOrderId,
            Long orderId,
            String orderNumber,
            LocalDateTime orderedAt,
            String representativeProductName,
            long additionalProductCount,
            long totalQuantity,
            long totalProductAmount,
            SellerOrderStatus status
    ) {
    }
}
