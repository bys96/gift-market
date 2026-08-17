package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrderStatus;

import java.util.List;

public enum BuyerOrderDeliveryStatus {
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    PAYMENT_EXPIRED,
    PAID,
    PREPARING,
    SHIPPING,
    DELIVERED,
    CANCELLED;

    public static BuyerOrderDeliveryStatus resolve(
            OrderStatus orderStatus,
            List<SellerOrderStatus> sellerOrderStatuses
    ) {
        return switch (orderStatus) {
            case PENDING_PAYMENT -> PAYMENT_PENDING;
            case PAYMENT_FAILED -> PAYMENT_FAILED;
            case PAYMENT_EXPIRED -> PAYMENT_EXPIRED;
            case CANCELLED -> CANCELLED;
            case ORDERED, PAID -> resolvePaidOrder(sellerOrderStatuses);
        };
    }

    private static BuyerOrderDeliveryStatus resolvePaidOrder(
            List<SellerOrderStatus> statuses
    ) {
        List<SellerOrderStatus> activeStatuses = statuses.stream()
                .filter(status -> status != SellerOrderStatus.CANCELLED)
                .toList();

        if (activeStatuses.isEmpty()) {
            return statuses.isEmpty() ? PAID : CANCELLED;
        }
        if (activeStatuses.stream().allMatch(
                status -> status == SellerOrderStatus.DELIVERED
        )) {
            return DELIVERED;
        }
        if (activeStatuses.stream().anyMatch(
                status -> status == SellerOrderStatus.SHIPPED
                        || status == SellerOrderStatus.DELIVERED
        )) {
            return SHIPPING;
        }
        if (activeStatuses.stream().anyMatch(
                status -> status == SellerOrderStatus.PREPARING
        )) {
            return PREPARING;
        }
        return PAID;
    }
}
