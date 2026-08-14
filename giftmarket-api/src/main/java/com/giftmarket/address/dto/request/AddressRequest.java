package com.giftmarket.address.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(

        @NotBlank(message = "배송지명을 입력해주세요.")
        @Size(
                max = 20,
                message = "배송지명은 20자 이하로 입력해주세요."
        )
        String name,

        @NotBlank(message = "받는 분 이름을 입력해주세요.")
        @Size(
                max = 30,
                message = "받는 분 이름은 30자 이하로 입력해주세요."
        )
        String recipientName,

        @NotBlank(message = "연락처를 입력해주세요.")
        @Pattern(
                regexp = "^0\\d{1,2}-\\d{3,4}-\\d{4}$",
                message = "올바른 연락처를 입력해주세요."
        )
        String phoneNumber,

        @NotBlank(message = "주소를 입력해주세요.")
        @Pattern(
                regexp = "^\\d{5}$",
                message = "올바른 주소를 입력해주세요."
        )
        String postalCode,

        @NotBlank(message = "주소를 입력해주세요.")
        @Size(
                max = 500,
                message = "주소는 500자 이하로 입력해주세요."
        )
        String address,

        @Size(
                max = 500,
                message = "상세주소는 500자 이하로 입력해주세요."
        )
        String detailAddress,

        boolean isDefault

) {
}