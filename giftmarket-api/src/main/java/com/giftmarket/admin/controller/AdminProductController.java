package com.giftmarket.admin.controller;

import com.giftmarket.admin.dto.request.AdminProductDeletedFilter;
import com.giftmarket.admin.dto.response.AdminProductDetailResponse;
import com.giftmarket.admin.dto.response.AdminProductPageResponse;
import com.giftmarket.admin.service.AdminProductService;
import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.product.entity.ProductStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    @GetMapping
    public ApiResponse<AdminProductPageResponse> getProducts(
            @AuthenticationPrincipal Long adminUserId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "ALL") AdminProductDeletedFilter deleted
    ) {
        return ApiResponse.success(adminProductService.getProducts(
                adminUserId, page, size, keyword, status, sellerId, categoryId, deleted
        ));
    }

    @GetMapping("/{productId}")
    public ApiResponse<AdminProductDetailResponse> getProduct(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long productId
    ) {
        return ApiResponse.success(adminProductService.getProduct(adminUserId, productId));
    }
}
