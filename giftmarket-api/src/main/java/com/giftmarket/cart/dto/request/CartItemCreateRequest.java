package com.giftmarket.cart.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CartItemCreateRequest(

        @NotNull(message = "상품을 선택해주세요.")
        Long productId,

        @NotNull(message = "수량을 입력해주세요.")
        @Positive(message = "수량은 1개 이상이어야 합니다.")
        Integer quantity

) {
}