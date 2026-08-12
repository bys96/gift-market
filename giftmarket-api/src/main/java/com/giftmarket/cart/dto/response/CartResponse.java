package com.giftmarket.cart.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CartResponse {

    private List<CartItemResponse> items;

    /**
     * 현재 구매 가능한 상품 금액 합계.
     */
    private Long totalProductPrice;

    /**
     * 현재 구매 가능한 상품 배송비 합계.
     */
    private Long totalShippingFee;

    /**
     * 현재 구매 가능한 상품 기준 최종 금액.
     */
    private Long totalPrice;

    /**
     * 장바구니에 존재하는 전체 상품 종류 수.
     */
    private Integer itemCount;

    public static CartResponse from(
            List<CartItemResponse> items
    ) {
        List<CartItemResponse> purchasableItems =
                items.stream()
                        .filter(CartItemResponse::isPurchasable)
                        .toList();

        long totalProductPrice =
                purchasableItems.stream()
                        .mapToLong(item ->
                                item.getPrice()
                                        * item.getQuantity()
                        )
                        .sum();

        long totalShippingFee =
                purchasableItems.stream()
                        .mapToLong(item ->
                                item.isFreeShipping()
                                        ? 0L
                                        : item.getShippingFee()
                        )
                        .sum();

        return CartResponse.builder()
                .items(items)
                .itemCount(items.size())
                .totalProductPrice(totalProductPrice)
                .totalShippingFee(totalShippingFee)
                .totalPrice(
                        totalProductPrice
                                + totalShippingFee
                )
                .build();
    }
}