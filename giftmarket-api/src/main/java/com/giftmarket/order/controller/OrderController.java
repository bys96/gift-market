package com.giftmarket.order.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.order.dto.request.OrderCreateRequest;
import com.giftmarket.order.dto.request.OrderCancelRequest;
import com.giftmarket.order.dto.request.OrderCancellationCreateRequest;
import com.giftmarket.order.dto.request.DirectOrderCreateRequest;
import com.giftmarket.order.dto.response.OrderCreateResponse;
import com.giftmarket.order.dto.response.OrderDetailResponse;
import com.giftmarket.order.dto.response.OrderSummaryResponse;
import com.giftmarket.order.dto.response.OrderCancelResponse;
import com.giftmarket.order.dto.response.OrderCancellationResponse;
import com.giftmarket.payment.service.PaymentCancellationService;
import com.giftmarket.order.service.OrderService;
import com.giftmarket.order.service.OrderCancellationWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentCancellationService paymentCancellationService;
    private final OrderCancellationWorkflowService orderCancellationWorkflowService;

    @PostMapping
    public ApiResponse<OrderCreateResponse> createOrder(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        return ApiResponse.success(
                orderService.createOrder(
                        userId,
                        request
                )
        );
    }

    @PostMapping("/direct")
    public ApiResponse<OrderCreateResponse> createDirectOrder(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody DirectOrderCreateRequest request
    ) {
        return ApiResponse.success(
                orderService.createDirectOrder(
                        userId,
                        request
                )
        );
    }

    @GetMapping
    public ApiResponse<List<OrderSummaryResponse>> getMyOrders(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(
                orderService.getMyOrders(userId)
        );
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getMyOrder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long orderId
    ) {
        return ApiResponse.success(
                orderService.getMyOrder(
                        userId,
                        orderId
                )
        );
    }

    @PatchMapping("/{orderId}/cancel")
    public ApiResponse<OrderCancelResponse> cancelOrder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long orderId,
            @Valid @RequestBody OrderCancelRequest request
    ) {
        return ApiResponse.success(
                paymentCancellationService.cancel(userId, orderId, request)
        );
    }

    @PostMapping("/{orderId}/cancellations")
    public ApiResponse<OrderCancellationResponse> createCancellation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long orderId,
            @Valid @RequestBody OrderCancellationCreateRequest request
    ) {
        return ApiResponse.success(
                orderCancellationWorkflowService.create(userId, orderId, request)
        );
    }

    @GetMapping("/{orderId}/cancellations")
    public ApiResponse<List<OrderCancellationResponse>> getCancellations(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long orderId
    ) {
        return ApiResponse.success(
                orderCancellationWorkflowService.getAllOwned(userId, orderId)
        );
    }
}
