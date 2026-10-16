package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.GiftOccasion;

import java.time.LocalDateTime;

public record AdminGiftOccasionResponse(
        Long id,
        String name,
        boolean active,
        Integer displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminGiftOccasionResponse from(GiftOccasion giftOccasion) {
        return new AdminGiftOccasionResponse(
                giftOccasion.getId(),
                giftOccasion.getName(),
                giftOccasion.isActive(),
                giftOccasion.getDisplayOrder(),
                giftOccasion.getCreatedAt(),
                giftOccasion.getUpdatedAt()
        );
    }
}
