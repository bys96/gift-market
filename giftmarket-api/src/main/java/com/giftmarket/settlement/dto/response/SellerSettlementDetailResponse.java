package com.giftmarket.settlement.dto.response;

import java.util.List;

public record SellerSettlementDetailResponse(
        SellerSettlementResponse settlement,
        List<SellerSettlementLedgerResponse> ledgerEntries
) {
}
