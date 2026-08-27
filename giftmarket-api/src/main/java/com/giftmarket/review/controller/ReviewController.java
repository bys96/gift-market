package com.giftmarket.review.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.review.dto.*;
import com.giftmarket.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping("/api/reviews")
    public ApiResponse<ReviewResponse> create(@AuthenticationPrincipal Long userId, @Valid @RequestBody ReviewUpsertRequest request) { return ApiResponse.success(reviewService.create(userId, request)); }
    @PatchMapping("/api/reviews/{reviewId}")
    public ApiResponse<ReviewResponse> update(@AuthenticationPrincipal Long userId, @PathVariable Long reviewId, @Valid @RequestBody ReviewUpdateRequest request) { return ApiResponse.success(reviewService.update(userId, reviewId, request)); }
    @DeleteMapping("/api/reviews/{reviewId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long reviewId) { reviewService.delete(userId, reviewId); return ApiResponse.success(null); }
    @GetMapping("/api/reviews/{reviewId}")
    public ApiResponse<ReviewEditResponse> getMine(@AuthenticationPrincipal Long userId, @PathVariable Long reviewId) { return ApiResponse.success(reviewService.getMine(userId, reviewId)); }
    @GetMapping("/api/reviews/order-items")
    public ApiResponse<Map<Long, Long>> reviewIds(@AuthenticationPrincipal Long userId, @RequestParam List<Long> ids) { return ApiResponse.success(reviewService.getReviewIds(userId, ids)); }
    @GetMapping("/api/reviews/order-items/{orderItemId}/eligibility")
    public ApiResponse<ReviewEligibilityResponse> eligibility(@AuthenticationPrincipal Long userId, @PathVariable Long orderItemId) { return ApiResponse.success(reviewService.getEligibility(userId, orderItemId)); }
    @GetMapping("/api/products/{productId}/reviews")
    public ApiResponse<ReviewPageResponse> list(@AuthenticationPrincipal Long userId, @PathVariable Long productId,
                                               @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) { return ApiResponse.success(reviewService.getProductReviews(productId, userId, page, size)); }
}
