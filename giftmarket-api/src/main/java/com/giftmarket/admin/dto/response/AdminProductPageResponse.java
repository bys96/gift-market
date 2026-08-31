package com.giftmarket.admin.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminProductPageResponse(
        List<AdminProductSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static AdminProductPageResponse from(Page<?> source, List<AdminProductSummaryResponse> content) {
        return new AdminProductPageResponse(
                content, source.getNumber(), source.getSize(), source.getTotalElements(),
                source.getTotalPages(), source.isFirst(), source.isLast()
        );
    }
}
