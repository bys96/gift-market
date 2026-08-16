package com.giftmarket.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentConfirmRequest(

        @NotBlank(message = "결제 키를 확인해주세요.")
        @Size(max = 200, message = "결제 키를 확인해주세요.")
        String providerPaymentKey,

        @NotBlank(message = "결제 주문번호를 확인해주세요.")
        @Size(max = 100, message = "결제 주문번호를 확인해주세요.")
        String merchantPaymentId,

        @NotNull(message = "결제 금액을 확인해주세요.")
        @Positive(message = "결제 금액을 확인해주세요.")
        Long amount
) {
}
