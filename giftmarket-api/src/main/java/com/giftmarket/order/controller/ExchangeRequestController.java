package com.giftmarket.order.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.order.dto.response.ExchangeRequestResponse;
import com.giftmarket.order.service.ExchangeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exchanges")
public class ExchangeRequestController {
    private final ExchangeRequestService exchangeRequestService;

    @GetMapping("/{exchangeRequestId}")
    public ApiResponse<ExchangeRequestResponse> getExchangeRequest(
            @AuthenticationPrincipal Long userId, @PathVariable Long exchangeRequestId
    ) {
        return ApiResponse.success(exchangeRequestService.getOwned(userId, exchangeRequestId));
    }
}
