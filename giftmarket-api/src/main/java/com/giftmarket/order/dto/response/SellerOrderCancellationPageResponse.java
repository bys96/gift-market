package com.giftmarket.order.dto.response;

import java.util.List;

public record SellerOrderCancellationPageResponse(
        List<SellerOrderCancellationResponse> cancellations,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
