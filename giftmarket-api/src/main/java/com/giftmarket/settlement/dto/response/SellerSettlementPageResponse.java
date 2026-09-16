package com.giftmarket.settlement.dto.response;

import java.util.List;

public record SellerSettlementPageResponse(
        List<SellerSettlementResponse> settlements,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
