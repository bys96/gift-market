package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.entity.Shipment;

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
            Shipment originalShipment,
            List<OrderItem> orderItems,
            Map<Long, Long> pendingCancellationQuantities,
            Map<Long, Integer> confirmableQuantities
    ) {
        return new BuyerSellerOrderResponse(
                sellerOrder.getId(),
                sellerOrder.getSeller().getStoreName(),
                sellerOrder.getStatus(),
                originalShipment == null ? sellerOrder.getShippingCompany() : originalShipment.getShippingCompany(),
                originalShipment == null ? sellerOrder.getTrackingNumber() : originalShipment.getTrackingNumber(),
                sellerOrder.getPreparedAt(),
                originalShipment == null ? sellerOrder.getShippedAt() : originalShipment.getShippedAt(),
                originalShipment == null ? sellerOrder.getDeliveredAt() : originalShipment.getDeliveredAt(),
                orderItems.stream()
                        .map(item -> OrderHistoryItemResponse.from(
                                item, pendingCancellationQuantities.getOrDefault(item.getId(), 0L),
                                confirmableQuantities.getOrDefault(item.getId(), 0)))
                        .toList()
        );
    }

    public static BuyerSellerOrderResponse from(
            SellerOrder sellerOrder,
            Shipment originalShipment,
            List<OrderItem> orderItems,
            Map<Long, Long> pendingCancellationQuantities
    ) {
        return from(sellerOrder, originalShipment, orderItems, pendingCancellationQuantities, Map.of());
    }

    public static BuyerSellerOrderResponse from(
            SellerOrder sellerOrder,
            List<OrderItem> orderItems,
            Map<Long, Long> pendingCancellationQuantities
    ) {
        return from(sellerOrder, null, orderItems, pendingCancellationQuantities, Map.of());
    }
}
