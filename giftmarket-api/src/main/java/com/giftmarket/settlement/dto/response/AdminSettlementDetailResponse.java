package com.giftmarket.settlement.dto.response;

import com.giftmarket.settlement.entity.Settlement;

import java.time.LocalDateTime;
import java.util.List;

public record AdminSettlementDetailResponse(
        AdminSettlementResponse settlement,
        String holdReason,
        LocalDateTime heldAt,
        Long heldByAdminUserId,
        LocalDateTime holdReleasedAt,
        Long holdReleasedByAdminUserId,
        Long confirmedByAdminUserId,
        List<AdminSettlementLedgerResponse> ledgerEntries
) {
    public static AdminSettlementDetailResponse from(
            Settlement settlement,
            List<AdminSettlementLedgerResponse> ledgerEntries
    ) {
        return new AdminSettlementDetailResponse(
                AdminSettlementResponse.from(settlement), settlement.getHoldReason(),
                settlement.getHeldAt(),
                settlement.getHeldByAdminUser() == null ? null : settlement.getHeldByAdminUser().getId(),
                settlement.getHoldReleasedAt(),
                settlement.getHoldReleasedByAdminUser() == null
                        ? null : settlement.getHoldReleasedByAdminUser().getId(),
                settlement.getConfirmedByAdminUser() == null
                        ? null : settlement.getConfirmedByAdminUser().getId(),
                ledgerEntries
        );
    }
}
