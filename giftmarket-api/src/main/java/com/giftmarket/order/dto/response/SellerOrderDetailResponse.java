package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record SellerOrderDetailResponse(
        Long sellerOrderId,
        Long orderId,
        String merchantOrderId,
        SellerOrderStatus status,
        LocalDateTime orderedAt,
        List<SellerOrderItemResponse> items,
        String recipientName,
        String recipientPhone,
        String postalCode,
        String address,
        String addressDetail,
        String shippingCompany,
        String trackingNumber,
        LocalDateTime preparedAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        List<SellerOrderCancellationSummaryResponse> cancellations
) {
    public static SellerOrderDetailResponse from(
            SellerOrder sellerOrder,
            List<OrderItem> items,
            List<SellerOrderCancellationSummaryResponse> cancellations
    ) {
        return new SellerOrderDetailResponse(
                sellerOrder.getId(),
                sellerOrder.getOrder().getId(),
                sellerOrder.getOrder().getOrderNumber(),
                sellerOrder.getStatus(),
                sellerOrder.getOrder().getOrderedAt(),
                items.stream().map(SellerOrderItemResponse::from).toList(),
                sellerOrder.getOrder().getRecipientName(),
                sellerOrder.getOrder().getRecipientPhone(),
                sellerOrder.getOrder().getPostalCode(),
                sellerOrder.getOrder().getAddress(),
                sellerOrder.getOrder().getAddressDetail(),
                sellerOrder.getShippingCompany(),
                sellerOrder.getTrackingNumber(),
                sellerOrder.getPreparedAt(),
                sellerOrder.getShippedAt(),
                sellerOrder.getDeliveredAt(),
                cancellations
        );
    }
}
