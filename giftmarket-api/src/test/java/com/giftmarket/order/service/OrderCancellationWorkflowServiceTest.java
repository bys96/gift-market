package com.giftmarket.order.service;

import com.giftmarket.order.dto.request.SellerOrderCancelRequest;
import com.giftmarket.order.dto.response.OrderCancellationResponse;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.payment.service.OrderCancellationRefundExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderCancellationWorkflowServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SELLER_ORDER_ID = 2L;
    private static final Long CANCELLATION_ID = 3L;

    @Mock OrderCancellationService cancellationService;
    @Mock SellerOrderManagementService sellerOrderManagementService;
    @Mock OrderCancellationRefundExecutionService refundExecutionService;

    private OrderCancellationWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new OrderCancellationWorkflowService(
                cancellationService, sellerOrderManagementService, refundExecutionService
        );
    }

    @Test
    void newlyCreatedSellerCancellationExecutesRefundAndReturnsCurrentResult() {
        SellerOrderCancelRequest request = request();
        OrderCancellationResponse requested = response(OrderCancellationStatus.REQUESTED);
        OrderCancellationResponse completed = response(OrderCancellationStatus.COMPLETED);
        given(sellerOrderManagementService.createCancelForExecution(
                USER_ID, SELLER_ORDER_ID, request
        )).willReturn(new SellerOrderCancellationCreateResult(requested, true));
        given(sellerOrderManagementService.getSellerCancellation(
                USER_ID, SELLER_ORDER_ID, CANCELLATION_ID
        )).willReturn(completed);

        OrderCancellationResponse response = workflowService.createBySeller(
                USER_ID, SELLER_ORDER_ID, request
        );

        verify(refundExecutionService).execute(CANCELLATION_ID);
        assertThat(response.status()).isEqualTo(OrderCancellationStatus.COMPLETED);
    }

    @Test
    void existingSellerCancellationDoesNotExecuteRefundAgain() {
        SellerOrderCancelRequest request = request();
        OrderCancellationResponse completed = response(OrderCancellationStatus.COMPLETED);
        given(sellerOrderManagementService.createCancelForExecution(
                USER_ID, SELLER_ORDER_ID, request
        )).willReturn(new SellerOrderCancellationCreateResult(completed, false));
        given(sellerOrderManagementService.getSellerCancellation(
                USER_ID, SELLER_ORDER_ID, CANCELLATION_ID
        )).willReturn(completed);

        OrderCancellationResponse response = workflowService.createBySeller(
                USER_ID, SELLER_ORDER_ID, request
        );

        verify(refundExecutionService, never()).execute(CANCELLATION_ID);
        assertThat(response.status()).isEqualTo(OrderCancellationStatus.COMPLETED);
    }

    private SellerOrderCancelRequest request() {
        return new SellerOrderCancelRequest(
                "123e4567-e89b-12d3-a456-426614174000", "판매자 취소"
        );
    }

    private OrderCancellationResponse response(OrderCancellationStatus status) {
        return new OrderCancellationResponse(
                CANCELLATION_ID, 4L, SELLER_ORDER_ID, status, "판매자 취소",
                null, null, null, null, null, null, List.of()
        );
    }
}
