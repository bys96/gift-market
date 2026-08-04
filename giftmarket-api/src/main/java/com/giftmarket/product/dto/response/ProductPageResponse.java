package com.giftmarket.product.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record ProductPageResponse(

        List<ProductListResponse> products,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last

) {

    public static ProductPageResponse from(Page<ProductListResponse> page) {
        return new ProductPageResponse(
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