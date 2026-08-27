package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.Shipment;

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
        Long refundedAmount,
        Long remainingPaymentAmount,

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
            Map<Long, Shipment> originalShipments,
            Map<Long, Long> pendingCancellationQuantities,
            Map<Long, Integer> confirmableQuantities,
            long refundedAmount,
            long remainingPaymentAmount
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
                refundedAmount,
                remainingPaymentAmount,
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getPostalCode(),
                order.getAddress(),
                order.getAddressDetail(),
                order.getOrderedAt(),
                order.getCancelledAt(),
                orderItems.stream()
                        .map(item -> OrderHistoryItemResponse.from(
                                item, pendingCancellationQuantities.getOrDefault(item.getId(), 0L),
                                confirmableQuantities.getOrDefault(item.getId(), 0)))
                        .toList(),
                sellerOrders.stream()
                        .map(sellerOrder -> BuyerSellerOrderResponse.from(
                                sellerOrder,
                                originalShipments.get(sellerOrder.getId()),
                                orderItems.stream()
                                        .filter(item -> item.getSellerOrder()
                                                .getId()
                                                .equals(sellerOrder.getId()))
                                        .toList(),
                                pendingCancellationQuantities,
                                confirmableQuantities
                        ))
                        .toList()
        );
    }

    public static OrderDetailResponse from(
            Order order,
            List<OrderItem> orderItems,
            List<SellerOrder> sellerOrders,
            Map<Long, Long> pendingCancellationQuantities
    ) {
        return from(
                order,
                orderItems,
                sellerOrders,
                Map.of(),
                pendingCancellationQuantities,
                Map.of(),
                0L,
                order.getTotalAmount()
        );
    }

    public static OrderDetailResponse from(
            Order order,
            List<OrderItem> orderItems,
            List<SellerOrder> sellerOrders,
            Map<Long, Shipment> originalShipments,
            Map<Long, Long> pendingCancellationQuantities
    ) {
        return from(order, orderItems, sellerOrders, originalShipments,
                pendingCancellationQuantities, Map.of(), 0L, order.getTotalAmount());
    }
}
