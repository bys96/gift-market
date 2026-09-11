package com.giftmarket.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerOrderCancelRequest(
        @NotBlank(message = "취소 요청 키를 입력해 주세요.")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "취소 요청 키는 UUID 형식이어야 합니다."
        )
        String clientRequestKey,

        @NotBlank(message = "취소 사유를 입력해 주세요.")
        @Size(max = 500, message = "취소 사유는 500자 이내로 입력해 주세요.")
        String cancelReason
) {
}
