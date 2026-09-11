package com.giftmarket.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerOrderCancelRequest(
        @NotBlank(message = "취소 사유를 입력해 주세요.")
        @Size(max = 500, message = "취소 사유는 500자 이내로 입력해 주세요.")
        String cancelReason
) {
}
