package com.giftmarket.cart.controller;

import com.giftmarket.cart.dto.request.CartItemBulkDeleteRequest;
import com.giftmarket.cart.dto.request.CartItemCreateRequest;
import com.giftmarket.cart.dto.request.CartItemQuantityUpdateRequest;
import com.giftmarket.cart.dto.response.CartResponse;
import com.giftmarket.cart.service.CartService;
import com.giftmarket.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartResponse> getCart(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(
                cartService.getCart(userId)
        );
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addCartItem(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CartItemCreateRequest request
    ) {
        return ApiResponse.success(
                cartService.addCartItem(
                        userId,
                        request
                )
        );
    }

    @PatchMapping("/items/{cartItemId}")
    public ApiResponse<CartResponse> updateQuantity(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long cartItemId,
            @Valid @RequestBody CartItemQuantityUpdateRequest request
    ) {
        return ApiResponse.success(
                cartService.updateQuantity(
                        userId,
                        cartItemId,
                        request
                )
        );
    }

    @DeleteMapping("/items/{cartItemId}")
    public ApiResponse<CartResponse> deleteCartItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long cartItemId
    ) {
        return ApiResponse.success(
                cartService.deleteCartItem(
                        userId,
                        cartItemId
                )
        );
    }

    @DeleteMapping("/items")
    public ApiResponse<CartResponse> deleteCartItems(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CartItemBulkDeleteRequest request
    ) {
        return ApiResponse.success(
                cartService.deleteCartItems(
                        userId,
                        request.cartItemIds()
                )
        );
    }

    @DeleteMapping
    public ApiResponse<Void> clearCart(
            @AuthenticationPrincipal Long userId
    ) {
        cartService.clearCart(userId);

        return ApiResponse.success(null);
    }
}