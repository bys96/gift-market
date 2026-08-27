package com.giftmarket.review.dto;

import java.util.List;

public record ReviewEditResponse(ReviewResponse review, List<String> imageObjectKeys) {}
