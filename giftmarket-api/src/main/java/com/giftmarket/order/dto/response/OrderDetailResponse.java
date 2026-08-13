package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(

        Long id,
        String orderNumber,
        OrderStatus status,

        Long totalProductAmount,
        Long totalShippingFee,
        Long totalAmount,

        String recipientName,
        String recipientPhone,
        String postalCode,
        String address,
        String addressDetail,

        LocalDateTime orderedAt,
        LocalDateTime cancelledAt,

        List<OrderHistoryItemResponse> items

) {

    public static OrderDetailResponse from(
            Order order,
            List<OrderItem> orderItems
    ) {
        return new OrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalProductAmount(),
                order.getTotalShippingFee(),
                order.getTotalAmount(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getPostalCode(),
                order.getAddress(),
                order.getAddressDetail(),
                order.getOrderedAt(),
                order.getCancelledAt(),
                orderItems.stream()
                        .map(OrderHistoryItemResponse::from)
                        .toList()
        );
    }
}