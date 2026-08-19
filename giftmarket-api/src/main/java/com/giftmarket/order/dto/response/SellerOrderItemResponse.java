package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.OrderItem;

public record SellerOrderItemResponse(
        Long orderItemId,
        Long productId,
        Long variantId,
        String productName,
        String brandName,
        String optionSnapshot,
        String representativeImageKey,
        long productPrice,
        long additionalPrice,
        long unitPrice,
        int quantity,
        int canceledQuantity,
        int remainingQuantity,
        long totalPrice,
        boolean freeShipping,
        long shippingFee
) {
    public static SellerOrderItemResponse from(OrderItem item) {
        return new SellerOrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getVariant() == null ? null : item.getVariant().getId(),
                item.getProductName(),
                item.getBrandName(),
                item.getOptionSnapshot(),
                item.getRepresentativeImageKey(),
                item.getProductPrice(),
                item.getAdditionalPrice(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getCanceledQuantity(),
                item.getRemainingQuantity(),
                item.getTotalPrice(),
                item.isFreeShipping(),
                item.getShippingFee()
        );
    }
}
