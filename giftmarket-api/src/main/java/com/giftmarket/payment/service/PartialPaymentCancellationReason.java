package com.giftmarket.payment.service;

public final class PartialPaymentCancellationReason {

    private static final String PREFIX = "구매자 주문 부분취소 요청 #";

    private PartialPaymentCancellationReason() {
    }

    public static String create(Long orderCancellationId) {
        if (orderCancellationId == null || orderCancellationId <= 0L) {
            throw new IllegalArgumentException("부분환불 주문 취소 ID가 필요합니다.");
        }
        return PREFIX + orderCancellationId;
    }
}
