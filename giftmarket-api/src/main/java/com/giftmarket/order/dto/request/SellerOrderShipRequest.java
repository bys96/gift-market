package com.giftmarket.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerOrderShipRequest(
        @NotBlank(message = "배송사를 입력해주세요.")
        @Size(max = 100, message = "배송사는 100자 이내로 입력해주세요.")
        String shippingCompany,

        @NotBlank(message = "운송장번호를 입력해주세요.")
        @Size(max = 100, message = "운송장번호는 100자 이내로 입력해주세요.")
        @Pattern(regexp = "^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$", message = "운송장번호는 영문, 숫자와 중간 하이픈만 사용할 수 있습니다.")
        String trackingNumber
) {
    public SellerOrderShipRequest {
        if (trackingNumber != null) trackingNumber = trackingNumber.strip();
    }
}
