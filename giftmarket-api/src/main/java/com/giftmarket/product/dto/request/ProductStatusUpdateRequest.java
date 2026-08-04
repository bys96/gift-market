package com.giftmarket.product.dto.request;

import com.giftmarket.product.entity.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ProductStatusUpdateRequest(

        @NotNull(message = "상품 상태를 선택해주세요.")
        ProductStatus status

) {
}