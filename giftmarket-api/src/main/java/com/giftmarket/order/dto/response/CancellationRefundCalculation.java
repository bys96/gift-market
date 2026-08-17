package com.giftmarket.order.dto.response;

import java.util.List;

public record CancellationRefundCalculation(
        Long cancellationId,
        long productRefundAmount,
        long shippingRefundAmount,
        long totalRefundAmount,
        boolean sellerOrderFullyCanceled,
        List<CancellationRefundItemCalculation> items
) {
    public CancellationRefundCalculation {
        items = List.copyOf(items);
    }
}
