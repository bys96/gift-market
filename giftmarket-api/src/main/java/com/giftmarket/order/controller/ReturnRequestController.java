package com.giftmarket.order.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.order.service.ReturnRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/returns")
public class ReturnRequestController {

    private final ReturnRequestService returnRequestService;

    @GetMapping("/{returnRequestId}")
    public ApiResponse<ReturnRequestResponse> getReturnRequest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long returnRequestId
    ) {
        return ApiResponse.success(returnRequestService.getOwned(userId, returnRequestId));
    }
}
