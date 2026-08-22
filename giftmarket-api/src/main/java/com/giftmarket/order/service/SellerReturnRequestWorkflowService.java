package com.giftmarket.order.service;

import com.giftmarket.order.dto.request.SellerReturnInspectRequest;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.payment.service.ReturnRefundExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerReturnRequestWorkflowService {
    private final SellerReturnRequestService returnRequestService;
    private final ReturnRefundExecutionService refundExecutionService;

    public ReturnRequestResponse inspect(Long userId, Long returnRequestId, SellerReturnInspectRequest request) {
        returnRequestService.inspect(userId, returnRequestId, request);
        refundExecutionService.execute(returnRequestId);
        return returnRequestService.getReturn(userId, returnRequestId);
    }
}
