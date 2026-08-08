package com.giftmarket.cart.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CartResponse {

    private List<CartItemResponse> items;

    /** 상품 금액 합계 */
    private Long totalProductPrice;

    /** 배송비 합계 */
    private Long totalShippingFee;

    /** 최종 결제 금액 */
    private Long totalPrice;

    /** 총 상품 수(종류) */
    private Integer itemCount;

    public static CartResponse from(
            List<CartItemResponse> items
    ) {
        long totalProductPrice = items.stream()
                .mapToLong(item ->
                        item.getPrice() * item.getQuantity())
                .sum();

        long totalShippingFee = items.stream()
                .mapToLong(item ->
                        item.isFreeShipping()
                                ? 0L
                                : item.getShippingFee())
                .sum();

        return CartResponse.builder()
                .items(items)
                .itemCount(items.size())
                .totalProductPrice(totalProductPrice)
                .totalShippingFee(totalShippingFee)
                .totalPrice(totalProductPrice + totalShippingFee)
                .build();
    }
}