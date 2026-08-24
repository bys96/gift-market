package com.giftmarket.payment.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.dto.response.ExchangeShippingPaymentResponse;
import com.giftmarket.payment.service.ExchangeShippingPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exchanges/{exchangeRequestId}/shipping-payment")
public class ExchangeShippingPaymentController {
    private final ExchangeShippingPaymentService service;

    @PostMapping("/prepare")
    public ApiResponse<ExchangeShippingPaymentResponse> prepare(@AuthenticationPrincipal Long userId,
                                                                 @PathVariable Long exchangeRequestId) {
        return ApiResponse.success(service.prepare(userId, exchangeRequestId));
    }

    @PostMapping("/confirm")
    public ApiResponse<ExchangeShippingPaymentResponse> confirm(@AuthenticationPrincipal Long userId,
                                                                 @PathVariable Long exchangeRequestId,
                                                                 @Valid @RequestBody PaymentConfirmRequest request) {
        return ApiResponse.success(service.confirm(userId, exchangeRequestId, request));
    }

    @GetMapping
    public ApiResponse<ExchangeShippingPaymentResponse> get(@AuthenticationPrincipal Long userId,
                                                             @PathVariable Long exchangeRequestId) {
        return ApiResponse.success(service.get(userId, exchangeRequestId));
    }
}
