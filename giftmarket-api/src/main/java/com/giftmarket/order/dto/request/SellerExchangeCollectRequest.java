package com.giftmarket.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerExchangeCollectRequest(
        @NotBlank @Size(max = 100) String shippingCompany,
        @NotBlank @Size(max = 100) String trackingNumber
) { }
