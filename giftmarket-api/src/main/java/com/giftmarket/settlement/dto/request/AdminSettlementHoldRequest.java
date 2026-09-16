package com.giftmarket.settlement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminSettlementHoldRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
