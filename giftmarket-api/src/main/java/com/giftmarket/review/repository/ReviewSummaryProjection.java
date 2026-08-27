package com.giftmarket.review.repository;

public interface ReviewSummaryProjection {
    Long getReviewCount();
    Double getAverageRating();
}
