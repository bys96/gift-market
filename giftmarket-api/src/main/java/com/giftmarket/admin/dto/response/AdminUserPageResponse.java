package com.giftmarket.admin.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminUserPageResponse(
        List<AdminUserSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static AdminUserPageResponse from(
            Page<?> source,
            List<AdminUserSummaryResponse> content
    ) {
        return new AdminUserPageResponse(
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
