package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record OrderDetailResponse(

        Long id,
        String orderNumber,
        OrderStatus status,
        BuyerOrderDeliveryStatus deliveryStatus,

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

        List<OrderHistoryItemResponse> items,
        List<BuyerSellerOrderResponse> sellerOrders

) {

    public static OrderDetailResponse from(
            Order order,
            List<OrderItem> orderItems,
            List<SellerOrder> sellerOrders,
            Map<Long, Long> pendingCancellationQuantities
    ) {
        return new OrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                BuyerOrderDeliveryStatus.resolve(
                        order.getStatus(),
                        sellerOrders.stream()
                                .map(SellerOrder::getStatus)
                                .toList()
                ),
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
                        .map(item -> OrderHistoryItemResponse.from(
                                item, pendingCancellationQuantities.getOrDefault(item.getId(), 0L)))
                        .toList(),
                sellerOrders.stream()
                        .map(sellerOrder -> BuyerSellerOrderResponse.from(
                                sellerOrder,
                                orderItems.stream()
                                        .filter(item -> item.getSellerOrder()
                                                .getId()
                                                .equals(sellerOrder.getId()))
                                        .toList(),
                                pendingCancellationQuantities
                        ))
                        .toList()
        );
    }
}
