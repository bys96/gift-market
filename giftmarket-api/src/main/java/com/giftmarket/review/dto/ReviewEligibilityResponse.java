package com.giftmarket.review.dto;

public record ReviewEligibilityResponse(Long orderItemId, Long reviewId, boolean eligible,
                                        String productName, String optionSnapshot) {}
