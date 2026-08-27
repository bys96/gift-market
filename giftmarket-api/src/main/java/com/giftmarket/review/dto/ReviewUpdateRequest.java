package com.giftmarket.review.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record ReviewUpdateRequest(
        @NotNull(message = "별점은 필수입니다.") @Min(1) @Max(5) Integer rating,
        @NotBlank(message = "리뷰 내용을 입력해주세요.") @Size(max = 2000) String content,
        @Size(max = 5, message = "리뷰 이미지는 최대 5장까지 첨부할 수 있습니다.") List<@NotBlank String> imageObjectKeys
) {}
