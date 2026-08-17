package com.giftmarket.order.service;

import com.giftmarket.order.dto.response.SellerOrderCancellationResponse;
import com.giftmarket.payment.service.OrderCancellationRefundExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerOrderCancellationWorkflowService {

    private final SellerOrderCancellationService cancellationService;
    private final OrderCancellationRefundExecutionService refundExecutionService;

    public SellerOrderCancellationResponse approve(Long userId, Long cancellationId) {
        cancellationService.approve(userId, cancellationId);
        refundExecutionService.execute(cancellationId);
        return cancellationService.getCancellation(userId, cancellationId);
    }
}
