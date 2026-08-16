package com.giftmarket.payment.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.dto.response.PaymentResponse;
import com.giftmarket.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{paymentId}/confirm")
    public ApiResponse<PaymentResponse> confirm(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        return ApiResponse.success(
                paymentService.confirm(userId, paymentId, request)
        );
    }

    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentResponse> getPayment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paymentId
    ) {
        return ApiResponse.success(
                paymentService.getPayment(userId, paymentId)
        );
    }
}
