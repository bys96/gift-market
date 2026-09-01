package com.giftmarket.admin.controller;

import com.giftmarket.admin.dto.request.AdminSellerSalesStatusChangeRequest;
import com.giftmarket.admin.dto.response.AdminSellerDetailResponse;
import com.giftmarket.admin.dto.response.AdminSellerPageResponse;
import com.giftmarket.admin.service.AdminSellerManagementService;
import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.seller.entity.SellerStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Validated
@RestController
@RequestMapping("/api/admin/sellers")
@RequiredArgsConstructor
public class AdminSellerManagementController {

    private final AdminSellerManagementService adminSellerManagementService;

    @GetMapping
    public ApiResponse<AdminSellerPageResponse> getSellers(
            @AuthenticationPrincipal Long adminUserId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SellerStatus status
    ) {
        return ApiResponse.success(adminSellerManagementService.getSellers(
                adminUserId, page, size, keyword, status
        ));
    }

    @GetMapping("/{sellerId}")
    public ApiResponse<AdminSellerDetailResponse> getSeller(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long sellerId
    ) {
        return ApiResponse.success(adminSellerManagementService.getSeller(adminUserId, sellerId));
    }

    @PatchMapping("/{sellerId}/suspend-sales")
    public ApiResponse<Void> suspendSales(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long sellerId,
            @Valid @RequestBody AdminSellerSalesStatusChangeRequest request
    ) {
        adminSellerManagementService.suspendSales(adminUserId, sellerId, request.trimmedReason());
        return ApiResponse.success(null);
    }

    @PatchMapping("/{sellerId}/reactivate-sales")
    public ApiResponse<Void> reactivateSales(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long sellerId,
            @Valid @RequestBody AdminSellerSalesStatusChangeRequest request
    ) {
        adminSellerManagementService.reactivateSales(adminUserId, sellerId, request.trimmedReason());
        return ApiResponse.success(null);
    }
}
