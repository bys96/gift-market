package com.giftmarket.seller.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record SellerApplicationPageResponse(
        List<SellerApplicationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static SellerApplicationPageResponse from(
            Page<SellerApplicationResponse> page
    ) {
        return new SellerApplicationPageResponse(
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
