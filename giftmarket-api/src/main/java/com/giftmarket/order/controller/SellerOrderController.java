package com.giftmarket.order.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.order.dto.request.SellerOrderShipRequest;
import com.giftmarket.order.dto.response.SellerOrderDetailResponse;
import com.giftmarket.order.dto.response.SellerOrderPageResponse;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.service.SellerOrderManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller/orders")
public class SellerOrderController {

    private final SellerOrderManagementService sellerOrderManagementService;

    @GetMapping
    public ApiResponse<SellerOrderPageResponse> getSellerOrders(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) SellerOrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(sellerOrderManagementService.getSellerOrders(
                userId, status, keyword, page, size
        ));
    }

    @GetMapping("/{sellerOrderId}")
    public ApiResponse<SellerOrderDetailResponse> getSellerOrder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sellerOrderId
    ) {
        return ApiResponse.success(
                sellerOrderManagementService.getSellerOrder(userId, sellerOrderId)
        );
    }

    @PatchMapping("/{sellerOrderId}/prepare")
    public ApiResponse<SellerOrderDetailResponse> prepare(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sellerOrderId
    ) {
        return ApiResponse.success(
                sellerOrderManagementService.prepare(userId, sellerOrderId)
        );
    }

    @PatchMapping("/{sellerOrderId}/ship")
    public ApiResponse<SellerOrderDetailResponse> ship(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sellerOrderId,
            @Valid @RequestBody SellerOrderShipRequest request
    ) {
        return ApiResponse.success(
                sellerOrderManagementService.ship(userId, sellerOrderId, request)
        );
    }

    @PatchMapping("/{sellerOrderId}/deliver")
    public ApiResponse<SellerOrderDetailResponse> deliver(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sellerOrderId
    ) {
        return ApiResponse.success(
                sellerOrderManagementService.deliver(userId, sellerOrderId)
        );
    }
}
