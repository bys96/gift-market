package com.giftmarket.settlement.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminSettlementPageResponse(
        List<AdminSettlementResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static AdminSettlementPageResponse from(Page<AdminSettlementResponse> page) {
        return new AdminSettlementPageResponse(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast()
        );
    }
}
