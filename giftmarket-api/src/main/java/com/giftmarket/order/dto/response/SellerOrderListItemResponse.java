package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.repository.SellerOrderItemSummaryProjection;

import java.time.LocalDateTime;

public record SellerOrderListItemResponse(
        Long sellerOrderId,
        Long orderId,
        String merchantOrderId,
        com.giftmarket.order.entity.SellerOrderStatus status,
        LocalDateTime orderedAt,
        String representativeProductName,
        long productTypeCount,
        long totalQuantity,
        long totalProductAmount,
        String recipientName,
        String shippingCompany,
        String trackingNumber
) {
    public static SellerOrderListItemResponse from(
            SellerOrder sellerOrder,
            SellerOrderItemSummaryProjection summary
    ) {
        return new SellerOrderListItemResponse(
                sellerOrder.getId(),
                sellerOrder.getOrder().getId(),
                sellerOrder.getOrder().getOrderNumber(),
                sellerOrder.getStatus(),
                sellerOrder.getOrder().getOrderedAt(),
                summary.getRepresentativeProductName(),
                summary.getProductTypeCount(),
                summary.getTotalQuantity(),
                summary.getTotalProductAmount(),
                sellerOrder.getOrder().getRecipientName(),
                sellerOrder.getShippingCompany(),
                sellerOrder.getTrackingNumber()
        );
    }
}
