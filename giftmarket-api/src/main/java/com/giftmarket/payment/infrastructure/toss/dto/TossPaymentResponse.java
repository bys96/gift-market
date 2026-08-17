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
        String approvedAt,
        Long balanceAmount,
        java.util.List<TossCancelResponse> cancels

) {

    public TossPaymentResponse(String paymentKey, String orderId, String status,
                               Long totalAmount, String currency, String method,
                               TossEasyPayResponse easyPay, String lastTransactionKey,
                               String approvedAt) {
        this(paymentKey, orderId, status, totalAmount, currency, method, easyPay,
                lastTransactionKey, approvedAt, null, null);
    }

    public record TossEasyPayResponse(

            String provider

    ) {
    }

    public record TossCancelResponse(Long cancelAmount, String canceledAt,
                                     String transactionKey, String cancelStatus) {
    }
}
