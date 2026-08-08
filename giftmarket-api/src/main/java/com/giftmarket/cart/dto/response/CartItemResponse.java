package com.giftmarket.cart.dto.response;

import com.giftmarket.cart.entity.CartItem;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CartItemResponse {

    private Long cartItemId;

    private Long productId;

    private Long sellerId;

    private String storeName;

    private String productName;

    private String brandName;

    private Long price;

    private Integer stockQuantity;

    private Integer quantity;

    private boolean freeShipping;

    private Long shippingFee;

    private String representativeImageKey;

    public static CartItemResponse from(
            CartItem cartItem
    ) {
        return CartItemResponse.builder()
                .cartItemId(cartItem.getId())
                .productId(cartItem.getProduct().getId())
                .sellerId(cartItem.getProduct().getSeller().getId())
                .storeName(cartItem.getProduct().getSeller().getStoreName())
                .productName(cartItem.getProduct().getName())
                .brandName(cartItem.getProduct().getBrandName())
                .price(cartItem.getProduct().getPrice())
                .stockQuantity(cartItem.getProduct().getStockQuantity())
                .quantity(cartItem.getQuantity())
                .freeShipping(cartItem.getProduct().isFreeShipping())
                .shippingFee(cartItem.getProduct().getShippingFee())
                .representativeImageKey(
                        cartItem.getProduct().getRepresentativeImageKey()
                )
                .build();
    }
}