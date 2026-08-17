package com.giftmarket.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrderCancelRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "취소 요청 키 형식이 올바르지 않습니다.")
        String clientCancelRequestKey,
        @NotBlank @Size(max = 200) String cancelReason
) {
}
