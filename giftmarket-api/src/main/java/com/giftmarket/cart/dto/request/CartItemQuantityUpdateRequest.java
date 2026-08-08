package com.giftmarket.cart.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CartItemQuantityUpdateRequest(

        @NotNull(message = "수량을 입력해주세요.")
        @Positive(message = "수량은 1개 이상이어야 합니다.")
        Integer quantity

) {
}