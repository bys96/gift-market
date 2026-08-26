package com.giftmarket.inquiry.dto;

import com.giftmarket.inquiry.entity.ProductInquiry;
import com.giftmarket.inquiry.entity.ProductInquiryStatus;
import com.giftmarket.inquiry.entity.ProductInquiryAnswer;

import java.time.LocalDateTime;

public record ProductInquiryResponse(
        Long id, Long productId, String productName, String title, String content,
        boolean isPrivate, boolean masked, ProductInquiryStatus status,
        String writerName, boolean mine, boolean editable,
        String answerContent, LocalDateTime answeredAt,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static ProductInquiryResponse from(ProductInquiry inquiry, ProductInquiryAnswer answer, Long viewerId) {
        boolean owner = viewerId != null && viewerId.equals(inquiry.getUser().getId());
        boolean seller = viewerId != null && viewerId.equals(inquiry.getProduct().getSeller().getUser().getId());
        boolean masked = inquiry.isPrivateInquiry() && !owner && !seller;
        String writer = maskName(inquiry.getUser().getName());
        return new ProductInquiryResponse(
                inquiry.getId(), inquiry.getProduct().getId(), inquiry.getProduct().getName(),
                masked ? "비공개 문의입니다." : inquiry.getTitle(),
                masked ? null : inquiry.getContent(), inquiry.isPrivateInquiry(), masked,
                inquiry.getStatus(), writer, owner,
                owner && inquiry.getStatus() == ProductInquiryStatus.WAITING,
                masked || answer == null ? null : answer.getContent(),
                answer == null ? null : answer.getCreatedAt(),
                inquiry.getCreatedAt(), inquiry.getUpdatedAt()
        );
    }

    private static String maskName(String name) {
        if (name == null || name.isBlank()) return "구매자";
        return name.substring(0, 1) + "**";
    }
}
