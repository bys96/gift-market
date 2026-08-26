package com.giftmarket.wishlist.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.product.dto.response.ProductSummaryResponse;
import com.giftmarket.wishlist.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ApiResponse<List<ProductSummaryResponse>> getWishlist(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(wishlistService.getWishlist(userId));
    }

    @PostMapping("/{productId}")
    public ApiResponse<ProductSummaryResponse> addWishlist(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId
    ) {
        return ApiResponse.success(wishlistService.addWishlist(userId, productId));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> removeWishlist(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId
    ) {
        wishlistService.removeWishlist(userId, productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/count")
    public ApiResponse<Long> countWishlist(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(wishlistService.countWishlist(userId));
    }
}
