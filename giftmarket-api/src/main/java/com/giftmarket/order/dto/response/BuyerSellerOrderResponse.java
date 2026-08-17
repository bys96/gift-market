package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
            List<OrderItem> orderItems,
            Map<Long, Long> pendingCancellationQuantities
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
                        .map(item -> OrderHistoryItemResponse.from(
                                item, pendingCancellationQuantities.getOrDefault(item.getId(), 0L)))
                        .toList()
        );
    }
}
