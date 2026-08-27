package com.giftmarket.order.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record BuyerOrderPageResponse(
        List<OrderSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static BuyerOrderPageResponse from(
            Page<OrderSummaryResponse> page
    ) {
        return new BuyerOrderPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
