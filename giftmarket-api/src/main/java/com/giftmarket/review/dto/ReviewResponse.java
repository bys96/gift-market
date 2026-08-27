package com.giftmarket.review.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewResponse(Long reviewId, int rating, String content, String writerName,
                             String productName, String optionSnapshot, long unitPriceSnapshot,
                             List<String> images, LocalDateTime createdAt, LocalDateTime updatedAt,
                             boolean mine) {}
