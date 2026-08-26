package com.giftmarket.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductInquiryAnswerRequest(
        @NotBlank(message = "답변 내용을 입력해주세요.")
        @Size(max = 2000, message = "답변 내용은 2000자 이하로 입력해주세요.")
        String content
) {}
