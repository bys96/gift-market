package com.giftmarket.order.dto.request;

import com.giftmarket.order.entity.ReturnResponsibility;

public record SellerReturnApproveRequest(
        ReturnResponsibility responsibility
) {
}
