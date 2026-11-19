package com.giftmarket.order.controller;

import com.giftmarket.cart.controller.CartController;
import com.giftmarket.cart.service.CartService;
import com.giftmarket.global.exception.GlobalExceptionHandler;
import com.giftmarket.order.dto.request.DirectOrderCreateRequest;
import com.giftmarket.order.service.*;
import com.giftmarket.payment.service.PaymentCancellationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MultiOptionPurchaseHttpContractTest {
    private final OrderService orders = mock(OrderService.class);
    private final CartService carts = mock(CartService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new OrderController(orders, mock(PaymentCancellationService.class),
                    mock(OrderCancellationWorkflowService.class), mock(ReturnRequestService.class),
                    mock(ExchangeRequestService.class), mock(PurchaseConfirmationService.class)),
            new CartController(carts))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    private String direct(String selection) {
        return """
                {"clientOrderRequestKey":"123e4567-e89b-42d3-a456-426614174000","productId":10,
                 "recipientName":"구매자","recipientPhone":"010-1234-5678","postalCode":"12345","address":"서울",
                """ + selection + "}";
    }

    @Test
    void directEndpointAcceptsBothLegacyAndMultiOptionBodies() throws Exception {
        mvc.perform(post("/api/orders/direct").contentType(MediaType.APPLICATION_JSON)
                .content(direct("\"items\":[{\"variantId\":1,\"quantity\":2},{\"variantId\":2,\"quantity\":2}]")))
                .andExpect(status().isOk());
        var request = ArgumentCaptor.forClass(DirectOrderCreateRequest.class);
        verify(orders).createDirectOrder(isNull(), request.capture());
        assertThat(request.getValue().items()).hasSize(2);
        mvc.perform(post("/api/orders/direct").contentType(MediaType.APPLICATION_JSON)
                .content(direct("\"variantId\":1,\"quantity\":2"))).andExpect(status().isOk());
    }

    @Test
    void directReplayConflictReturns409() throws Exception {
        when(orders.createDirectOrder(isNull(), any()))
                .thenThrow(new com.giftmarket.order.exception.OrderRequestConflictException());
        mvc.perform(post("/api/orders/direct").contentType(MediaType.APPLICATION_JSON)
                .content(direct("\"variantId\":1,\"quantity\":2")))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidDirectSelectionsNeverReachOrderService() throws Exception {
        for (String selection : new String[] {"\"items\":[]", "\"items\":[null]",
                "\"items\":[{\"variantId\":1,\"quantity\":0}]",
                "\"items\":[{\"variantId\":1,\"quantity\":2}],\"quantity\":2", "\"quantity\":null"}) {
            mvc.perform(post("/api/orders/direct").contentType(MediaType.APPLICATION_JSON)
                    .content(direct(selection))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(orders);
    }

    @Test
    void bulkCartEndpointValidatesEveryItemBeforeCallingService() throws Exception {
        mvc.perform(post("/api/cart/items/bulk").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":10,\"variantId\":1,\"quantity\":2,\"expectedUnitPrice\":10000}]}"))
                .andExpect(status().isOk());
        verify(carts).addCartItems(isNull(), argThat(items -> items.size() == 1 && items.getFirst().expectedUnitPrice() == 10000L));
        clearInvocations(carts);
        for (String body : new String[] {"{\"items\":[]}", "{\"items\":[null]}",
                "{\"items\":[{\"productId\":10,\"quantity\":0}]}", "{}"}) {
            mvc.perform(post("/api/cart/items/bulk").contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(carts);
    }
}
