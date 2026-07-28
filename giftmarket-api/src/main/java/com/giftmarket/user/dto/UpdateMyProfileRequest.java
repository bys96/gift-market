package com.giftmarket.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(

        @NotBlank(message = "이름은 필수입니다.")
        @Size(
                max = 30,
                message = "이름은 30자 이하로 입력해 주세요."
        )
        String name
) {

    public String trimmedName() {
        return name.trim();
    }
}