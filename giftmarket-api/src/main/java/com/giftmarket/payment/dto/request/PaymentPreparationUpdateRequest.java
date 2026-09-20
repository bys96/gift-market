package com.giftmarket.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentPreparationUpdateRequest(
        @NotBlank(message = "받는 분 이름을 입력해주세요.")
        @Size(max = 100, message = "받는 분 이름은 100자 이하입니다.")
        String recipientName,

        @NotBlank(message = "연락처를 입력해주세요.")
        @Pattern(
                regexp = "^[0-9\\-]{9,20}$",
                message = "올바른 연락처를 입력해주세요."
        )
        String recipientPhone,

        @NotBlank(message = "우편번호를 입력해주세요.")
        @Size(max = 20, message = "우편번호는 20자 이하입니다.")
        String postalCode,

        @NotBlank(message = "주소를 입력해주세요.")
        @Size(max = 500, message = "주소는 500자 이하입니다.")
        String address,

        @Size(max = 500, message = "상세주소는 500자 이하입니다.")
        String addressDetail
) {
}
