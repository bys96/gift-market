package com.giftmarket.order.dto.response;

public record ReturnRefundCalculation(
        long productRefundAmount,
        long originalShippingRefundAmount,
        long returnShippingCharge,
        long refundAmount,
        boolean fullSellerOrderReturn
) {
}
