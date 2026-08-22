package com.giftmarket.order.service;

import com.giftmarket.order.dto.request.SellerReturnInspectRequest;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.payment.service.ReturnRefundExecutionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SellerReturnRequestWorkflowServiceTest {
    @Test
    void commitsInspectionThenExecutesRefundAndReloadsResponse() {
        SellerReturnRequestService returns = mock(SellerReturnRequestService.class);
        ReturnRefundExecutionService refunds = mock(ReturnRefundExecutionService.class);
        SellerReturnRequestWorkflowService service = new SellerReturnRequestWorkflowService(returns, refunds);
        SellerReturnInspectRequest request = mock(SellerReturnInspectRequest.class);
        ReturnRequestResponse response = mock(ReturnRequestResponse.class);
        when(returns.getReturn(1L, 10L)).thenReturn(response);

        ReturnRequestResponse actual = service.inspect(1L, 10L, request);

        var order = inOrder(returns, refunds);
        order.verify(returns).inspect(1L, 10L, request);
        order.verify(refunds).execute(10L);
        order.verify(returns).getReturn(1L, 10L);
        assertThat(actual).isSameAs(response);
    }
}
