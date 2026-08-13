package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderSummaryResponse(

        Long id,
        String orderNumber,
        OrderStatus status,
        LocalDateTime orderedAt,

        Long totalProductAmount,
        Long totalShippingFee,
        Long totalAmount,

        List<OrderHistoryItemResponse> items

) {

    public static OrderSummaryResponse from(
            Order order,
            List<OrderItem> orderItems
    ) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getOrderedAt(),
                order.getTotalProductAmount(),
                order.getTotalShippingFee(),
                order.getTotalAmount(),
                orderItems.stream()
                        .map(OrderHistoryItemResponse::from)
                        .toList()
        );
    }
}