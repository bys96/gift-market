package com.giftmarket.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductInquiryRequest(
        @NotBlank(message = "문의 제목을 입력해주세요.")
        @Size(max = 100, message = "문의 제목은 100자 이하로 입력해주세요.")
        String title,
        @NotBlank(message = "문의 내용을 입력해주세요.")
        @Size(max = 2000, message = "문의 내용은 2000자 이하로 입력해주세요.")
        String content,
        boolean isPrivate
) {}
