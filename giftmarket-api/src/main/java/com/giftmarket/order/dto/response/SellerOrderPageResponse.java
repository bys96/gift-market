package com.giftmarket.order.dto.response;

import java.util.List;

public record SellerOrderPageResponse(
        List<SellerOrderListItemResponse> orders,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
