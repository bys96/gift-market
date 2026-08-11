package com.giftmarket.product.draft.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProductDraftCreateRequest(

        Long productId,

        @NotBlank(message = "임시저장 데이터를 확인해주세요.")
        String draftData

) {
}