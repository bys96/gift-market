package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderItem;

public record OrderHistoryItemResponse(

        Long id,
        Long productId,
        Long variantId,
        String productName,
        String brandName,
        String optionSnapshot,
        String representativeImageKey,
        Integer quantity,
        Integer canceledQuantity,
        Integer availableCancellationQuantity,
        Integer confirmedQuantity,
        Integer confirmableQuantity,
        Long unitPrice,
        Long totalPrice

) {

    public static OrderHistoryItemResponse from(
            OrderItem orderItem
    ) {
        return new OrderHistoryItemResponse(
                orderItem.getId(),
                orderItem.getProduct().getId(),
                orderItem.getVariant() == null
                        ? null
                        : orderItem.getVariant().getId(),
                orderItem.getProductName(),
                orderItem.getBrandName(),
                orderItem.getOptionSnapshot(),
                orderItem.getRepresentativeImageKey(),
                orderItem.getQuantity(),
                orderItem.getCanceledQuantity(),
                Math.max(0, orderItem.getQuantity() - orderItem.getCanceledQuantity()
                        - orderItem.getConfirmedQuantity()),
                orderItem.getConfirmedQuantity(),
                0,
                orderItem.getUnitPrice(),
                orderItem.getTotalPrice()
        );
    }

    public static OrderHistoryItemResponse from(
            OrderItem orderItem,
            long pendingCancellationQuantity,
            int confirmableQuantity
    ) {
        long available = (long) orderItem.getQuantity()
                - orderItem.getCanceledQuantity()
                - orderItem.getConfirmedQuantity()
                - pendingCancellationQuantity;
        return new OrderHistoryItemResponse(
                orderItem.getId(), orderItem.getProduct().getId(),
                orderItem.getVariant() == null ? null : orderItem.getVariant().getId(),
                orderItem.getProductName(), orderItem.getBrandName(), orderItem.getOptionSnapshot(),
                orderItem.getRepresentativeImageKey(), orderItem.getQuantity(),
                orderItem.getCanceledQuantity(), Math.toIntExact(Math.max(0L, available)),
                orderItem.getConfirmedQuantity(), confirmableQuantity,
                orderItem.getUnitPrice(), orderItem.getTotalPrice());
    }

    public static OrderHistoryItemResponse from(OrderItem orderItem, long pendingCancellationQuantity) {
        return from(orderItem, pendingCancellationQuantity, 0);
    }
}
