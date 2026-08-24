package com.giftmarket.payment.dto.response;

import com.giftmarket.payment.entity.ExchangeShippingPayment;
import com.giftmarket.payment.entity.ExchangeShippingPaymentStatus;
import java.time.LocalDateTime;

public record ExchangeShippingPaymentResponse(
        Long paymentId, Long exchangeRequestId, ExchangeShippingPaymentStatus status,
        long amount, String providerOrderId, String idempotencyKey,
        LocalDateTime paymentDueAt, String userMessage
) {
    public static ExchangeShippingPaymentResponse from(ExchangeShippingPayment payment) {
        return new ExchangeShippingPaymentResponse(payment.getId(), payment.getExchangeRequest().getId(),
                payment.getStatus(), payment.getAmount(), payment.getProviderOrderId(), payment.getIdempotencyKey(),
                payment.getExchangeRequest().getPaymentDueAt(), message(payment.getStatus()));
    }
    private static String message(ExchangeShippingPaymentStatus status) {
        return switch (status) {
            case READY -> "교환 배송비 결제를 기다리고 있습니다.";
            case REQUESTED -> "결제 결과를 확인 중입니다.";
            case SUCCEEDED -> "교환 배송비 결제가 완료되었습니다.";
            case FAILED -> "결제가 거절되었습니다. 기한 내 다시 시도해주세요.";
            case EXPIRED -> "교환 배송비 결제 기한이 만료되었습니다.";
            case COMPENSATION_REQUIRED -> "만료 후 결제가 확인되어 결제 취소를 처리 중입니다.";
        };
    }
}
