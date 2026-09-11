package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.SellerOrderStatus;

public record SellerOrderCancelValidationResponse(
        Long sellerOrderId,
        SellerOrderStatus sellerOrderStatus,
        String message
) {
    public static SellerOrderCancelValidationResponse validated(
            Long sellerOrderId,
            SellerOrderStatus sellerOrderStatus
    ) {
        return new SellerOrderCancelValidationResponse(
                sellerOrderId,
                sellerOrderStatus,
                "판매자 주문 취소 요청 검증이 완료되었습니다. 실제 취소는 처리되지 않았습니다."
        );
    }
}
