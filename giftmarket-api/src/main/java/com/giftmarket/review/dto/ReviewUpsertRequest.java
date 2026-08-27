package com.giftmarket.review.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record ReviewUpsertRequest(
        @NotNull(message = "주문 상품은 필수입니다.") @Positive Long orderItemId,
        @NotNull(message = "별점은 필수입니다.") @Min(value = 1, message = "별점은 1점 이상이어야 합니다.") @Max(value = 5, message = "별점은 5점 이하여야 합니다.") Integer rating,
        @NotBlank(message = "리뷰 내용을 입력해주세요.") @Size(max = 2000, message = "리뷰 내용은 2000자 이하로 입력해주세요.") String content,
        @Size(max = 5, message = "리뷰 이미지는 최대 5장까지 첨부할 수 있습니다.") List<@NotBlank(message = "이미지 키를 확인해주세요.") String> imageObjectKeys
) {}
