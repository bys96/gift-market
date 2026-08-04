package com.giftmarket.seller.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.seller.dto.response.SellerResponse;
import com.giftmarket.seller.service.SellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sellers")
public class SellerProfileController {

    private final SellerService sellerService;

    @GetMapping("/me")
    public ApiResponse<SellerResponse> getMySeller(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(
                sellerService.getMySeller(userId)
        );
    }
}