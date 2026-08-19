package com.giftmarket.payment.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TossCancelRequest(String cancelReason, Long cancelAmount) {
    public TossCancelRequest {
        if (cancelReason == null || cancelReason.isBlank()
                || cancelReason.length() > 200) {
            throw new IllegalArgumentException("Toss 취소 사유는 200자 이내여야 합니다.");
        }
    }
}
