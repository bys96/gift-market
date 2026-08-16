package com.giftmarket.payment.infrastructure.toss.dto;

public record TossPaymentResponse(

        String paymentKey,
        String orderId,
        String status,
        Long totalAmount,
        String currency,
        String method,
        TossEasyPayResponse easyPay,
        String lastTransactionKey,
        String approvedAt

) {

    public record TossEasyPayResponse(

            String provider

    ) {
    }
}
