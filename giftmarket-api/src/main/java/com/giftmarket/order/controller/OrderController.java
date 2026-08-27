package com.giftmarket.order.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.order.dto.request.OrderCreateRequest;
import com.giftmarket.order.dto.request.OrderCancelRequest;
import com.giftmarket.order.dto.request.OrderCancellationCreateRequest;
import com.giftmarket.order.dto.request.DirectOrderCreateRequest;
import com.giftmarket.order.dto.request.ReturnRequestCreateRequest;
import com.giftmarket.order.dto.request.ExchangeRequestCreateRequest;
import com.giftmarket.order.dto.response.OrderCreateResponse;
import com.giftmarket.order.dto.response.OrderDetailResponse;
import com.giftmarket.order.dto.response.OrderSummaryResponse;
import com.giftmarket.order.dto.response.OrderCancelResponse;
import com.giftmarket.order.dto.response.OrderCancellationResponse;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.order.dto.response.ExchangeRequestResponse;
import com.giftmarket.order.dto.response.PurchaseConfirmationResponse;
import com.giftmarket.order.dto.response.BuyerOrderPageResponse;
import com.giftmarket.payment.service.PaymentCancellationService;
import com.giftmarket.order.service.OrderService;
import com.giftmarket.order.service.OrderCancellationWorkflowService;
import com.giftmarket.order.service.ReturnRequestService;
import com.giftmarket.order.service.ExchangeRequestService;
import com.giftmarket.order.service.PurchaseConfirmationService;
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
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentCancellationService paymentCancellationService;
    private final OrderCancellationWorkflowService orderCancellationWorkflowService;
    private final ReturnRequestService returnRequestService;
    private final ExchangeRequestService exchangeRequestService;
    private final PurchaseConfirmationService purchaseConfirmationService;

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
    public ApiResponse<BuyerOrderPageResponse> getMyOrders(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(
                orderService.getMyOrders(userId, page, size)
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

    @PostMapping("/{orderId}/items/{orderItemId}/confirm")
    public ApiResponse<PurchaseConfirmationResponse> confirmPurchase(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long orderId,
            @PathVariable Long orderItemId
    ) {
        return ApiResponse.success(purchaseConfirmationService.confirm(userId, orderId, orderItemId));
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

    @PostMapping("/{orderId}/seller-orders/{sellerOrderId}/returns")
    public ApiResponse<ReturnRequestResponse> createReturnRequest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long orderId,
            @PathVariable Long sellerOrderId,
            @Valid @RequestBody ReturnRequestCreateRequest request
    ) {
        return ApiResponse.success(
                returnRequestService.create(userId, orderId, sellerOrderId, request)
        );
    }

    @GetMapping("/{orderId}/returns")
    public ApiResponse<List<ReturnRequestResponse>> getReturnRequests(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long orderId
    ) {
        return ApiResponse.success(returnRequestService.getAllOwned(userId, orderId));
    }

    @PostMapping("/{orderId}/seller-orders/{sellerOrderId}/exchanges")
    public ApiResponse<ExchangeRequestResponse> createExchangeRequest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long orderId,
            @PathVariable Long sellerOrderId,
            @Valid @RequestBody ExchangeRequestCreateRequest request
    ) {
        return ApiResponse.success(exchangeRequestService.create(userId, orderId, sellerOrderId, request));
    }

    @GetMapping("/{orderId}/exchanges")
    public ApiResponse<List<ExchangeRequestResponse>> getExchangeRequests(
            @AuthenticationPrincipal Long userId, @PathVariable Long orderId
    ) {
        return ApiResponse.success(exchangeRequestService.getAllOwned(userId, orderId));
    }
}
