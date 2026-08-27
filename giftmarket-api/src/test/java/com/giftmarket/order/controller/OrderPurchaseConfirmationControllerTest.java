package com.giftmarket.order.controller;

import com.giftmarket.order.dto.response.PurchaseConfirmationResponse;
import com.giftmarket.order.dto.response.BuyerOrderPageResponse;
import com.giftmarket.order.service.*;
import com.giftmarket.payment.service.PaymentCancellationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class OrderPurchaseConfirmationControllerTest {
    @Test void passesBuyerOrderPaginationAndAuthenticatedUserToService() {
        OrderService orders = mock(OrderService.class);
        OrderController controller = new OrderController(
                orders, mock(PaymentCancellationService.class),
                mock(OrderCancellationWorkflowService.class), mock(ReturnRequestService.class),
                mock(ExchangeRequestService.class), mock(PurchaseConfirmationService.class));
        var page = new BuyerOrderPageResponse(
                java.util.List.of(), 2, 15, 31, 3, false, true);
        given(orders.getMyOrders(7L, 2, 15)).willReturn(page);

        assertThat(controller.getMyOrders(7L, 2, 15).data()).isSameAs(page);
        verify(orders).getMyOrders(7L, 2, 15);
    }

    @Test void passesAuthenticatedUserAndPathOwnershipKeysToService() {
        PurchaseConfirmationService confirmations = mock(PurchaseConfirmationService.class);
        OrderController controller = new OrderController(
                mock(OrderService.class), mock(PaymentCancellationService.class),
                mock(OrderCancellationWorkflowService.class), mock(ReturnRequestService.class),
                mock(ExchangeRequestService.class), confirmations);
        var response = new PurchaseConfirmationResponse(30L, 2, 0);
        given(confirmations.confirm(1L, 10L, 30L)).willReturn(response);

        assertThat(controller.confirmPurchase(1L, 10L, 30L).data()).isSameAs(response);
        verify(confirmations).confirm(1L, 10L, 30L);
    }
}
