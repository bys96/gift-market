package com.giftmarket.seller.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.seller.dto.request.SellerStoreUpdateRequest;
import com.giftmarket.seller.dto.response.SellerStoreResponse;
import com.giftmarket.seller.service.SellerStoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/seller/store")
public class SellerStoreController {
    private final SellerStoreService service;
    @GetMapping public ApiResponse<SellerStoreResponse> get(@AuthenticationPrincipal Long userId) { return ApiResponse.success(service.get(userId)); }
    @PatchMapping public ApiResponse<SellerStoreResponse> update(@AuthenticationPrincipal Long userId, @Valid @RequestBody SellerStoreUpdateRequest request) { return ApiResponse.success(service.update(userId, request)); }
}
