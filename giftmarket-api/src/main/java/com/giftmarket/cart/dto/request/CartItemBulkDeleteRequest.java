package com.giftmarket.cart.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CartItemBulkDeleteRequest(

        @NotEmpty(message = "삭제할 장바구니 상품을 선택해주세요.")
        List<
                @NotNull(message = "장바구니 상품 번호가 필요합니다.")
                @Positive(message = "올바르지 않은 장바구니 상품 번호입니다.")
                        Long
                > cartItemIds

) {
}