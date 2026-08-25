package com.giftmarket.seller.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.seller.dto.response.SellerDashboardResponse;
import com.giftmarket.seller.service.SellerDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller/dashboard")
public class SellerDashboardController {

    private final SellerDashboardService sellerDashboardService;

    @GetMapping
    public ApiResponse<SellerDashboardResponse> getDashboard(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(sellerDashboardService.getDashboard(userId));
    }
}
