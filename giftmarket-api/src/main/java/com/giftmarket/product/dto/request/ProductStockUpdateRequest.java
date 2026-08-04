package com.giftmarket.product.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductStockUpdateRequest(

        @NotNull(message = "재고 수량을 입력해주세요.")
        @PositiveOrZero(message = "재고는 0개 이상이어야 합니다.")
        Integer stockQuantity

) {
}