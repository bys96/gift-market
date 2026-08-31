package com.giftmarket.admin.dto.response;

import org.springframework.data.domain.Page;
import java.util.List;

public record AdminOrderPageResponse(List<AdminOrderSummaryResponse> content, int page, int size,
                                     long totalElements, int totalPages, boolean first, boolean last) {
    public static AdminOrderPageResponse from(Page<?> page, List<AdminOrderSummaryResponse> content) {
        return new AdminOrderPageResponse(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
