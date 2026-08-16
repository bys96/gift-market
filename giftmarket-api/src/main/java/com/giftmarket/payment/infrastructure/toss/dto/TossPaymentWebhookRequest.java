package com.giftmarket.payment.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentWebhookRequest(

        @NotBlank
        String eventType,

        @NotBlank
        String createdAt,

        @Valid
        @NotNull
        PaymentData data

) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentData(

            @NotBlank
            String paymentKey,

            @NotBlank
            String orderId,

            @NotBlank
            String status

    ) {
    }
}
