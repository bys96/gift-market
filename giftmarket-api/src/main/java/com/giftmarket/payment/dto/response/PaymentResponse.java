package com.giftmarket.payment.dto.response;

import com.giftmarket.payment.entity.EasyPayProvider;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentMethod;
import com.giftmarket.payment.entity.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        PaymentStatus status,
        Long amount,
        PaymentMethod method,
        EasyPayProvider easyPayProvider,
        LocalDateTime approvedAt,
        LocalDateTime expiresAt,
        String userMessage
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getEasyPayProvider(),
                payment.getApprovedAt(),
                payment.getExpiresAt(),
                messageFor(payment.getStatus())
        );
    }

    private static String messageFor(PaymentStatus status) {
        return switch (status) {
            case READY -> "결제 승인을 기다리고 있습니다.";
            case CONFIRMING -> "결제 결과를 확인 중입니다.";
            case PAID -> "결제가 완료되었습니다.";
            case FAILED -> "결제에 실패했습니다. 결제 정보를 다시 확인해주세요.";
            case EXPIRED -> "결제 가능 시간이 만료되었습니다.";
            case CANCELING -> "결제 취소를 처리 중입니다.";
            case CANCELED -> "결제가 취소되었습니다.";
        };
    }
}
