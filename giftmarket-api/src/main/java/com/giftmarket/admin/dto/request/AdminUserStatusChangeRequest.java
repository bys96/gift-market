package com.giftmarket.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserStatusChangeRequest(
        @NotBlank(message = "사유를 입력해주세요.")
        @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
        String reason
) {
    public String trimmedReason() {
        return reason.trim();
    }
}
