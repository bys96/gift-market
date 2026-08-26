package com.giftmarket.inquiry.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record ProductInquiryPageResponse(
        List<ProductInquiryResponse> inquiries, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last
) {
    public static ProductInquiryPageResponse from(Page<ProductInquiryResponse> page) {
        return new ProductInquiryPageResponse(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
