package com.giftmarket.seller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerApplicationCreateRequest(

        @NotBlank(message = "상점명을 입력해주세요.")
        @Size(max = 100, message = "상점명은 100자 이하입니다.")
        String storeName,

        @Size(max = 1000, message = "소개는 1000자 이하입니다.")
        String introduction

) {
}