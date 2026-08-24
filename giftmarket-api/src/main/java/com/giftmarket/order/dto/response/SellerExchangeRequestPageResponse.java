package com.giftmarket.order.dto.response;

import java.util.List;

public record SellerExchangeRequestPageResponse(
        List<ExchangeRequestResponse> exchanges,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
