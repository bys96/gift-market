package com.giftmarket.product.dto.response;

import java.util.List;

public record HomeProductPriceRangeResponse(
        String code,
        List<ProductSummaryResponse> products
) {

    public HomeProductPriceRangeResponse {
        products = List.copyOf(products);
    }
}
