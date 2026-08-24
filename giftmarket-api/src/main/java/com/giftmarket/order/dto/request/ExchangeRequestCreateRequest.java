package com.giftmarket.order.dto.request;

import com.giftmarket.order.entity.ExchangeReasonType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExchangeRequestCreateRequest(
        @NotBlank
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "교환 요청 키는 UUID 형식이어야 합니다."
        )
        String clientRequestKey,
        @NotNull ExchangeReasonType reasonType,
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 100) String collectionRecipientName,
        @NotBlank @Size(max = 30) String collectionPhone,
        @NotBlank @Size(max = 20) String collectionPostalCode,
        @NotBlank @Size(max = 255) String collectionAddress,
        @Size(max = 255) String collectionAddressDetail,
        @NotBlank @Size(max = 100) String reshippingRecipientName,
        @NotBlank @Size(max = 30) String reshippingPhone,
        @NotBlank @Size(max = 20) String reshippingPostalCode,
        @NotBlank @Size(max = 255) String reshippingAddress,
        @Size(max = 255) String reshippingAddressDetail,
        @NotNull @Size(min = 1, max = 100) List<@Valid ExchangeRequestItemRequest> items,
        @Size(max = 5) List<@NotBlank @Size(max = 500) String> imageObjectKeys
) {
}
