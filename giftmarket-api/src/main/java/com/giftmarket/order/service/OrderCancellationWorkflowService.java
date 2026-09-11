package com.giftmarket.order.service;

import com.giftmarket.order.dto.request.OrderCancellationCreateRequest;
import com.giftmarket.order.dto.response.OrderCancellationResponse;
import com.giftmarket.payment.service.OrderCancellationRefundExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCancellationWorkflowService {

    private final OrderCancellationService cancellationService;
    private final SellerOrderManagementService sellerOrderManagementService;
    private final OrderCancellationRefundExecutionService refundExecutionService;

    public OrderCancellationResponse create(
            Long userId,
            Long orderId,
            OrderCancellationCreateRequest request
    ) {
        OrderCancellationResponse created = cancellationService.create(userId, orderId, request);
        refundExecutionService.execute(created.cancellationId());
        return cancellationService.getOwned(userId, orderId, created.cancellationId());
    }

    public List<OrderCancellationResponse> getAllOwned(Long userId, Long orderId) {
        return cancellationService.getAllOwned(userId, orderId);
    }

    public OrderCancellationResponse createBySeller(
            Long userId,
            Long sellerOrderId,
            com.giftmarket.order.dto.request.SellerOrderCancelRequest request
    ) {
        SellerOrderCancellationCreateResult result = sellerOrderManagementService
                .createCancelForExecution(userId, sellerOrderId, request);
        if (result.newlyCreated()) {
            refundExecutionService.execute(result.cancellation().cancellationId());
        }
        return sellerOrderManagementService.getSellerCancellation(
                userId, sellerOrderId, result.cancellation().cancellationId()
        );
    }
}
