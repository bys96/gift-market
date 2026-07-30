package com.giftmarket.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(

        @NotBlank(message = "이름은 필수입니다.")
        @Size(
                max = 30,
                message = "이름은 30자 이하로 입력해 주세요."
        )
        String name,

        @Size(
                max = 1000,
                message = "프로필 이미지 경로는 1000자 이하로 입력해 주세요."
        )
        String profileImageUrl
) {

    public String trimmedName() {
        return name.trim();
    }

    public String trimmedProfileImageUrl() {
        return profileImageUrl == null
                ? null
                : profileImageUrl.trim();
    }
}