package com.giftmarket.seller.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.seller.dto.request.SellerApplicationCreateRequest;
import com.giftmarket.seller.dto.response.SellerApplicationResponse;
import com.giftmarket.seller.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller-applications")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @PostMapping
    public ApiResponse<SellerApplicationResponse> apply(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SellerApplicationCreateRequest request
    ) {
        return ApiResponse.success(
                sellerService.apply(userId, request)
        );
    }

    @GetMapping("/me/latest")
    public ApiResponse<SellerApplicationResponse> getMyLatestApplication(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(
                sellerService.getMyLatestApplication(userId)
        );
    }
}