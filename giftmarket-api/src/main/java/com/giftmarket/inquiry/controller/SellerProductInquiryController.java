package com.giftmarket.inquiry.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.inquiry.dto.*;
import com.giftmarket.inquiry.entity.ProductInquiryStatus;
import com.giftmarket.inquiry.service.SellerProductInquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller/product-inquiries")
public class SellerProductInquiryController {
    private final SellerProductInquiryService inquiryService;

    @GetMapping
    public ApiResponse<ProductInquiryPageResponse> list(@AuthenticationPrincipal Long userId, @RequestParam(required = false) ProductInquiryStatus status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(inquiryService.getInquiries(userId, status, page, size));
    }

    @GetMapping("/{inquiryId}")
    public ApiResponse<ProductInquiryResponse> detail(@AuthenticationPrincipal Long userId, @PathVariable Long inquiryId) {
        return ApiResponse.success(inquiryService.getInquiry(userId, inquiryId));
    }

    @PatchMapping("/{inquiryId}/answer")
    public ApiResponse<ProductInquiryResponse> answer(@AuthenticationPrincipal Long userId, @PathVariable Long inquiryId, @Valid @RequestBody ProductInquiryAnswerRequest request) {
        return ApiResponse.success(inquiryService.answer(userId, inquiryId, request));
    }
}
