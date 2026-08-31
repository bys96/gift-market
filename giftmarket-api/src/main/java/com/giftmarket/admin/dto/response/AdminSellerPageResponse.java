package com.giftmarket.admin.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminSellerPageResponse(
        List<AdminSellerSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static AdminSellerPageResponse from(Page<?> source, List<AdminSellerSummaryResponse> content) {
        return new AdminSellerPageResponse(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast()
        );
    }
}
