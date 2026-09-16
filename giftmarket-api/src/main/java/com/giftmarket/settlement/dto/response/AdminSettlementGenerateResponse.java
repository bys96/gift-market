package com.giftmarket.settlement.dto.response;

public record AdminSettlementGenerateResponse(
        boolean created,
        AdminSettlementResponse settlement
) {
}
