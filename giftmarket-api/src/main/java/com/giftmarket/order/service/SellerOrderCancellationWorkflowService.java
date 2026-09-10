package com.giftmarket.order.service;

import com.giftmarket.order.dto.response.SellerOrderCancellationResponse;
import com.giftmarket.order.dto.request.SellerOrderCancelRequest;
import com.giftmarket.payment.service.OrderCancellationRefundExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerOrderCancellationWorkflowService {

    private final SellerOrderCancellationService cancellationService;
    private final OrderCancellationRefundExecutionService refundExecutionService;

    public SellerOrderCancellationResponse createAndExecute(Long userId, Long sellerOrderId,
                                                              SellerOrderCancelRequest request) {
        SellerOrderCancellationResponse created = cancellationService.create(userId, sellerOrderId, request);
        refundExecutionService.execute(created.cancellationId());
        return cancellationService.getCancellationBySeller(userId, created.cancellationId());
    }

    public SellerOrderCancellationResponse approve(Long userId, Long cancellationId) {
        cancellationService.approve(userId, cancellationId);
        refundExecutionService.execute(cancellationId);
        return cancellationService.getCancellation(userId, cancellationId);
    }
}
