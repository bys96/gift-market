package com.giftmarket.admin.dto.response;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderSummaryResponse(
        Long orderId, String orderNumber, OrderStatus orderStatus, LocalDateTime orderedAt,
        Long totalProductAmount, Long totalShippingFee, Long totalAmount,
        Long userId, String userName, String userEmail,
        Long paymentId, PaymentStatus paymentStatus,
        String representativeProductName, long productTypeCount, long totalItemCount,
        int sellerOrderCount, List<SellerOrderStatus> sellerOrderStatuses
) {
    public static AdminOrderSummaryResponse from(Order order, Payment payment, String productName,
                                                  long productTypeCount, long totalItemCount,
                                                  List<SellerOrderStatus> statuses) {
        return new AdminOrderSummaryResponse(order.getId(), order.getOrderNumber(), order.getStatus(),
                order.getOrderedAt(), order.getTotalProductAmount(), order.getTotalShippingFee(), order.getTotalAmount(),
                order.getUser().getId(), order.getUser().getName(), order.getUser().getEmail(),
                payment == null ? null : payment.getId(), payment == null ? null : payment.getStatus(),
                productName, productTypeCount, totalItemCount, statuses.size(), statuses);
    }
}
