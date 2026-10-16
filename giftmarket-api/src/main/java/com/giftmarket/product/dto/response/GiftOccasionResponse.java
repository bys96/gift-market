package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.GiftOccasion;

public record GiftOccasionResponse(Long id, String name) {

    public static GiftOccasionResponse from(GiftOccasion giftOccasion) {
        return new GiftOccasionResponse(giftOccasion.getId(), giftOccasion.getName());
    }
}
