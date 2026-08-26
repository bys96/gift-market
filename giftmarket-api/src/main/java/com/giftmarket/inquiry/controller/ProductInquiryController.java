package com.giftmarket.inquiry.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.inquiry.dto.*;
import com.giftmarket.inquiry.service.ProductInquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products/{productId}/inquiries")
public class ProductInquiryController {
    private final ProductInquiryService inquiryService;

    @GetMapping
    public ApiResponse<ProductInquiryPageResponse> list(@AuthenticationPrincipal Long viewerId, @PathVariable Long productId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(inquiryService.getInquiries(viewerId, productId, page, size));
    }

    @PostMapping
    public ApiResponse<ProductInquiryResponse> create(@AuthenticationPrincipal Long userId, @PathVariable Long productId, @Valid @RequestBody ProductInquiryRequest request) {
        return ApiResponse.success(inquiryService.create(userId, productId, request));
    }

    @PatchMapping("/{inquiryId}")
    public ApiResponse<ProductInquiryResponse> update(@AuthenticationPrincipal Long userId, @PathVariable Long productId, @PathVariable Long inquiryId, @Valid @RequestBody ProductInquiryRequest request) {
        return ApiResponse.success(inquiryService.update(userId, productId, inquiryId, request));
    }

    @DeleteMapping("/{inquiryId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long productId, @PathVariable Long inquiryId) {
        inquiryService.delete(userId, productId, inquiryId);
        return ApiResponse.success(null);
    }
}
