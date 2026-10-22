package com.giftmarket.product.dto.response;

import java.util.List;

public record HomeProductResponse(
        List<GiftOccasionResponse> giftOccasions,
        List<ProductSummaryResponse> popularProducts,
        List<HomeProductPriceRangeResponse> priceRanges,
        List<ProductSummaryResponse> newestProducts
) {

    public HomeProductResponse {
        giftOccasions = List.copyOf(giftOccasions);
        popularProducts = List.copyOf(popularProducts);
        priceRanges = List.copyOf(priceRanges);
        newestProducts = List.copyOf(newestProducts);
    }
}
