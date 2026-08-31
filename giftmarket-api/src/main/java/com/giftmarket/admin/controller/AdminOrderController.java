package com.giftmarket.admin.controller;

import com.giftmarket.admin.dto.response.*;
import com.giftmarket.admin.service.AdminOrderService;
import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.payment.entity.PaymentStatus;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {
    private final AdminOrderService adminOrderService;

    @GetMapping
    public ApiResponse<AdminOrderPageResponse> getOrders(
            @AuthenticationPrincipal Long adminUserId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OrderStatus orderStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) SellerOrderStatus sellerOrderStatus) {
        return ApiResponse.success(adminOrderService.getOrders(adminUserId, page, size, keyword,
                orderStatus, paymentStatus, sellerOrderStatus));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<AdminOrderDetailResponse> getOrder(@AuthenticationPrincipal Long adminUserId,
                                                          @PathVariable Long orderId) {
        return ApiResponse.success(adminOrderService.getOrder(adminUserId, orderId));
    }
}
