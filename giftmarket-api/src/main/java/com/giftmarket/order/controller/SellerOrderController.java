package com.giftmarket.order.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.order.dto.request.SellerOrderShipRequest;
import com.giftmarket.order.dto.request.SellerOrderCancellationRejectRequest;
import com.giftmarket.order.dto.request.SellerReturnApproveRequest;
import com.giftmarket.order.dto.request.SellerReturnCollectRequest;
import com.giftmarket.order.dto.request.SellerReturnInspectRequest;
import com.giftmarket.order.dto.request.SellerReturnRejectRequest;
import com.giftmarket.order.dto.response.SellerOrderDetailResponse;
import com.giftmarket.order.dto.response.SellerOrderPageResponse;
import com.giftmarket.order.dto.response.SellerOrderCancellationPageResponse;
import com.giftmarket.order.dto.response.SellerOrderCancellationResponse;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.order.dto.response.SellerReturnRequestPageResponse;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.service.SellerOrderManagementService;
import com.giftmarket.order.service.SellerOrderCancellationService;
import com.giftmarket.order.service.SellerOrderCancellationWorkflowService;
import com.giftmarket.order.service.SellerReturnRequestService;
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
    private final SellerOrderCancellationService sellerOrderCancellationService;
    private final SellerOrderCancellationWorkflowService sellerOrderCancellationWorkflowService;
    private final SellerReturnRequestService sellerReturnRequestService;

    @GetMapping("/returns")
    public ApiResponse<SellerReturnRequestPageResponse> getReturns(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) ReturnRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(sellerReturnRequestService.getReturns(userId, status, page, size));
    }

    @GetMapping("/returns/{returnRequestId}")
    public ApiResponse<ReturnRequestResponse> getReturn(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long returnRequestId
    ) {
        return ApiResponse.success(sellerReturnRequestService.getReturn(userId, returnRequestId));
    }

    @PatchMapping("/returns/{returnRequestId}/approve")
    public ApiResponse<ReturnRequestResponse> approveReturn(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long returnRequestId,
            @Valid @RequestBody SellerReturnApproveRequest request
    ) {
        return ApiResponse.success(sellerReturnRequestService.approve(
                userId, returnRequestId, request.responsibility()
        ));
    }

    @PatchMapping("/returns/{returnRequestId}/reject")
    public ApiResponse<ReturnRequestResponse> rejectReturn(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long returnRequestId,
            @Valid @RequestBody SellerReturnRejectRequest request
    ) {
        return ApiResponse.success(sellerReturnRequestService.reject(
                userId, returnRequestId, request.reason()
        ));
    }

    @PatchMapping("/returns/{returnRequestId}/collect")
    public ApiResponse<ReturnRequestResponse> collectReturn(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long returnRequestId,
            @Valid @RequestBody SellerReturnCollectRequest request
    ) {
        return ApiResponse.success(sellerReturnRequestService.collect(
                userId, returnRequestId, request.shippingCompany(), request.trackingNumber()
        ));
    }

    @PatchMapping("/returns/{returnRequestId}/receive")
    public ApiResponse<ReturnRequestResponse> receiveReturn(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long returnRequestId
    ) {
        return ApiResponse.success(sellerReturnRequestService.receive(userId, returnRequestId));
    }

    @PatchMapping("/returns/{returnRequestId}/inspect")
    public ApiResponse<ReturnRequestResponse> inspectReturn(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long returnRequestId,
            @Valid @RequestBody SellerReturnInspectRequest request
    ) {
        return ApiResponse.success(sellerReturnRequestService.inspect(userId, returnRequestId, request));
    }

    @GetMapping("/cancellations")
    public ApiResponse<SellerOrderCancellationPageResponse> getCancellations(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) OrderCancellationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                sellerOrderCancellationService.getCancellations(
                        userId, status, page, size
                )
        );
    }

    @GetMapping("/cancellations/{cancellationId}")
    public ApiResponse<SellerOrderCancellationResponse> getCancellation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long cancellationId
    ) {
        return ApiResponse.success(
                sellerOrderCancellationService.getCancellation(userId, cancellationId)
        );
    }

    @PatchMapping("/cancellations/{cancellationId}/approve")
    public ApiResponse<SellerOrderCancellationResponse> approveCancellation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long cancellationId
    ) {
        return ApiResponse.success(
                sellerOrderCancellationWorkflowService.approve(userId, cancellationId)
        );
    }

    @PatchMapping("/cancellations/{cancellationId}/reject")
    public ApiResponse<SellerOrderCancellationResponse> rejectCancellation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long cancellationId,
            @Valid @RequestBody SellerOrderCancellationRejectRequest request
    ) {
        return ApiResponse.success(
                sellerOrderCancellationService.reject(
                        userId,
                        cancellationId,
                        request.reason()
                )
        );
    }

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
