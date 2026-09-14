package com.giftmarket.inquiry.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.inquiry.dto.ProductInquiryPageResponse;
import com.giftmarket.inquiry.service.ProductInquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class BuyerProductInquiryController {

    private final ProductInquiryService inquiryService;

    @GetMapping("/me")
    public ApiResponse<ProductInquiryPageResponse> getMyInquiries(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(inquiryService.getMyInquiries(userId, page, size));
    }
}
