package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.payment.entity.PaymentStatus;

public record OrderCancelResponse(Long orderId, OrderStatus orderStatus,
                                  PaymentStatus paymentStatus, String message) {
}
