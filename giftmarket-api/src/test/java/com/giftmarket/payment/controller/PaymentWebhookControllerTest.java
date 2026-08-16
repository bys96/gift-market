package com.giftmarket.payment.controller;

import com.giftmarket.payment.exception.PaymentWebhookRetryableException;
import com.giftmarket.payment.service.TossPaymentWebhookService;
import com.giftmarket.auth.jwt.JwtTokenProvider;
import com.giftmarket.auth.service.CustomOidcUserService;
import com.giftmarket.auth.handler.OAuth2AuthenticationSuccessHandler;
import com.giftmarket.global.config.SecurityConfig;
import com.giftmarket.global.exception.GlobalExceptionHandler;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PaymentWebhookController.class,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PaymentWebhookControllerTest {

    private static final String BODY = """
            {
              "eventType": "PAYMENT_STATUS_CHANGED",
              "createdAt": "2026-08-16T12:00:00+09:00",
              "data": {
                "paymentKey": "provider-key",
                "orderId": "GM-PAY",
                "status": "DONE",
                "card": {"number": "ignored"}
              }
            }
            """;

    @Autowired MockMvc mockMvc;
    @MockitoBean TossPaymentWebhookService webhookService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean CustomOidcUserService customOidcUserService;
    @MockitoBean OAuth2AuthenticationSuccessHandler authenticationSuccessHandler;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void webhookDoesNotRequireJwtAndReturnsOk() throws Exception {
        mockMvc.perform(post("/api/payments/webhooks/toss")
                        .header(
                                "tosspayments-webhook-transmission-id",
                                "transmission-id"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void retryableFailureReturnsServiceUnavailable() throws Exception {
        doThrow(new PaymentWebhookRetryableException("retry"))
                .when(webhookService)
                .process(eq("transmission-id"), any());

        mockMvc.perform(post("/api/payments/webhooks/toss")
                        .header(
                                "tosspayments-webhook-transmission-id",
                                "transmission-id"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void missingTransmissionIdIsRejected() throws Exception {
        mockMvc.perform(post("/api/payments/webhooks/toss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());
    }
}
