package com.giftmarket.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerOrderShipRequest(
        @NotBlank(message = "배송사를 입력해주세요.")
        @Size(max = 100, message = "배송사는 100자 이내로 입력해주세요.")
        String shippingCompany,

        @NotBlank(message = "운송장번호를 입력해주세요.")
        @Size(max = 100, message = "운송장번호는 100자 이내로 입력해주세요.")
        String trackingNumber
) {
}
