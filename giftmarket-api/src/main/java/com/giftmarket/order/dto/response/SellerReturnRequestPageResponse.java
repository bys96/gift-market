package com.giftmarket.order.dto.response;

import java.util.List;

public record SellerReturnRequestPageResponse(
        List<ReturnRequestResponse> returns,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
