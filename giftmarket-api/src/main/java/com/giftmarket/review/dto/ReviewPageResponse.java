package com.giftmarket.review.dto;

import java.util.List;

public record ReviewPageResponse(List<ReviewResponse> reviews, int page, int size,
                                 long totalElements, int totalPages, double averageRating,
                                 long reviewCount) {}
