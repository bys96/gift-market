package com.giftmarket.product.dto.response;

import java.util.List;

public record ProductSuggestionsResponse(List<Item> products, List<String> keywords) {
    public record Item(Long id, String name, String imageUrl, Long price) {}
}
