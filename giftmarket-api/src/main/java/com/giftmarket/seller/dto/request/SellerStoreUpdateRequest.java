package com.giftmarket.seller.dto.request;

import jakarta.validation.constraints.*;

public record SellerStoreUpdateRequest(
        @NotBlank @Size(min = 2, max = 30) String storeName,
        @Size(max = 500) String introduction,
        @Size(max = 500) String logoImageKey,
        @Size(max = 500) String bannerImageKey,
        @Size(max = 30) String customerServicePhone,
        @Email @Size(max = 255) String customerServiceEmail,
        @Size(max = 255) String customerServiceHours
) {}
