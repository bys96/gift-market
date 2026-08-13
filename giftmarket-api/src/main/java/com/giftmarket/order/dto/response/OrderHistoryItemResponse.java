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
                orderItem.getUnitPrice(),
                orderItem.getTotalPrice()
        );
    }
}