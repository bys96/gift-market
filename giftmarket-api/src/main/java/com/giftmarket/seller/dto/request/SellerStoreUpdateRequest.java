package com.giftmarket.seller.dto.request;

import jakarta.validation.constraints.*;

public record SellerStoreUpdateRequest(
        @NotBlank @Size(min = 2, max = 30) String storeName,
        @Size(max = 500) String introduction,
        @Size(max = 500) String logoImageKey,
        @Size(max = 500) String bannerImageKey,
        @Size(max = 30) String customerServicePhone,
        @Email @Size(max = 255) String customerServiceEmail,
        @Pattern(regexp = "^$|(?:[01][0-9]|2[0-3]):[0-5][0-9]", message = "상담 시작 시간은 HH:mm 형식으로 입력해 주세요.")
        String customerServiceOpenTime,
        @Pattern(regexp = "^$|(?:[01][0-9]|2[0-3]):[0-5][0-9]", message = "상담 종료 시간은 HH:mm 형식으로 입력해 주세요.")
        String customerServiceCloseTime,
        @Size(max = 255) String customerServiceClosedDays,
        @Size(max = 500) String customerServiceNote
) {}
