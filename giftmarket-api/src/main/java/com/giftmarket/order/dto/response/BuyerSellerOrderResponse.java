package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record BuyerSellerOrderResponse(
        Long sellerOrderId,
        String sellerName,
        SellerOrderStatus status,
        String shippingCompany,
        String trackingNumber,
        LocalDateTime preparedAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        List<OrderHistoryItemResponse> items
) {
    public static BuyerSellerOrderResponse from(
            SellerOrder sellerOrder,
            List<OrderItem> orderItems
    ) {
        return new BuyerSellerOrderResponse(
                sellerOrder.getId(),
                sellerOrder.getSeller().getStoreName(),
                sellerOrder.getStatus(),
                sellerOrder.getShippingCompany(),
                sellerOrder.getTrackingNumber(),
                sellerOrder.getPreparedAt(),
                sellerOrder.getShippedAt(),
                sellerOrder.getDeliveredAt(),
                orderItems.stream()
                        .map(OrderHistoryItemResponse::from)
                        .toList()
        );
    }
}
