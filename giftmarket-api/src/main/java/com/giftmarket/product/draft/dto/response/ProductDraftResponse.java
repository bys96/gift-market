package com.giftmarket.product.draft.dto.response;

import com.giftmarket.product.draft.entity.ProductDraft;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ProductDraftResponse(

        Long id,
        Long productId,
        String draftData,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {

    public static ProductDraftResponse from(
            ProductDraft productDraft
    ) {
        return ProductDraftResponse.builder()
                .id(productDraft.getId())
                .productId(
                        productDraft.getProduct() == null
                                ? null
                                : productDraft.getProduct().getId()
                )
                .draftData(productDraft.getDraftData())
                .createdAt(productDraft.getCreatedAt())
                .updatedAt(productDraft.getUpdatedAt())
                .build();
    }
}