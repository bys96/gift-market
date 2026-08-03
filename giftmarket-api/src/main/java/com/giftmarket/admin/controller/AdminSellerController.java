package com.giftmarket.admin.controller;

import com.giftmarket.admin.service.AdminSellerService;
import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.seller.dto.request.SellerApplicationRejectRequest;
import com.giftmarket.seller.dto.response.SellerApplicationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/seller-applications")
@RequiredArgsConstructor
public class AdminSellerController {

    private final AdminSellerService adminSellerService;

    @GetMapping("/pending")
    public ApiResponse<List<SellerApplicationResponse>>
    getPendingApplications(
            @AuthenticationPrincipal Long adminUserId
    ) {
        return ApiResponse.success(
                adminSellerService.getPendingApplications(
                        adminUserId
                )
        );
    }

    @PatchMapping("/{applicationId}/approve")
    public ApiResponse<SellerApplicationResponse> approve(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long applicationId
    ) {
        return ApiResponse.success(
                adminSellerService.approve(
                        adminUserId,
                        applicationId
                )
        );
    }

    @PatchMapping("/{applicationId}/reject")
    public ApiResponse<SellerApplicationResponse> reject(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long applicationId,
            @Valid @RequestBody
            SellerApplicationRejectRequest request
    ) {
        return ApiResponse.success(
                adminSellerService.reject(
                        adminUserId,
                        applicationId,
                        request
                )
        );
    }
}