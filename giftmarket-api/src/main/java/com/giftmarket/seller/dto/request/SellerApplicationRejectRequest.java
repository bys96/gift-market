package com.giftmarket.seller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerApplicationRejectRequest(

        @NotBlank(message = "거절 사유를 입력해주세요.")
        @Size(
                max = 500,
                message = "거절 사유는 500자 이하입니다."
        )
        String rejectionReason

) {

    public String trimmedRejectionReason() {
        return rejectionReason.trim();
    }
}