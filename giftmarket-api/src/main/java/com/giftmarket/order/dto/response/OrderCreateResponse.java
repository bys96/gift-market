package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;

import java.time.LocalDateTime;

public record OrderCreateResponse(

        Long orderId,

        String orderNumber,

        OrderStatus status,

        Long paymentId,

        String merchantPaymentId,

        PaymentStatus paymentStatus,

        String orderName,

        Long amount,

        Long totalProductAmount,

        Long totalShippingFee,

        Long totalAmount,

        LocalDateTime orderedAt,

        LocalDateTime expiresAt

) {

    public static OrderCreateResponse from(
            Order order,
            Payment payment,
            String orderName
    ) {
        return new OrderCreateResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                payment.getId(),
                payment.getMerchantPaymentId(),
                payment.getStatus(),
                orderName,
                order.getTotalAmount(),
                order.getTotalProductAmount(),
                order.getTotalShippingFee(),
                order.getTotalAmount(),
                order.getOrderedAt(),
                payment.getExpiresAt()
        );
    }
}
