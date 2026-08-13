package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderStatus;

import java.time.LocalDateTime;

public record OrderCreateResponse(

        Long orderId,

        String orderNumber,

        OrderStatus status,

        Long totalProductAmount,

        Long totalShippingFee,

        Long totalAmount,

        LocalDateTime orderedAt

) {

    public static OrderCreateResponse from(
            Order order
    ) {
        return new OrderCreateResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalProductAmount(),
                order.getTotalShippingFee(),
                order.getTotalAmount(),
                order.getOrderedAt()
        );
    }
}