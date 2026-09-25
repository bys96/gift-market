package com.giftmarket.settlement.service;

import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import com.giftmarket.settlement.repository.SettlementRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SettlementManagementService {

    private final SettlementRepository settlementRepository;
    private final SettlementLedgerEntryRepository ledgerRepository;
    private final UserRepository userRepository;
    private final SettlementLedgerAggregator ledgerAggregator;

    @Transactional
    public Settlement hold(Long settlementId, Long adminUserId, String reason) {
        User admin = requireAdmin(adminUserId);
        Settlement settlement = findForUpdate(settlementId);
        try {
            settlement.hold(reason, admin, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new SettlementException(exception.getMessage());
        }
        return settlement;
    }

    @Transactional
    public Settlement releaseHold(Long settlementId, Long adminUserId) {
        User admin = requireAdmin(adminUserId);
        Settlement settlement = findForUpdate(settlementId);
        try {
            settlement.releaseHold(admin, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new SettlementException(exception.getMessage());
        }
        return settlement;
    }

    @Transactional
    public Settlement confirm(Long settlementId, Long adminUserId) {
        User admin = requireAdmin(adminUserId);
        Settlement settlement = findForUpdate(settlementId);
        List<SettlementLedgerEntry> entries = ledgerRepository.findAllBySettlementIdForUpdate(
                settlement.getId()
        );
        validateEntryOwnership(settlement, entries);
        validateSnapshot(settlement, ledgerAggregator.aggregate(entries));
        try {
            settlement.confirm(admin, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new SettlementException(exception.getMessage());
        }
        return settlement;
    }

    private User requireAdmin(Long adminUserId) {
        if (adminUserId == null || adminUserId <= 0L) {
            throw new SettlementException("관리자 정보가 올바르지 않습니다.");
        }
        User user = userRepository.findById(adminUserId)
                .orElseThrow(() -> new SettlementException("관리자를 찾을 수 없습니다."));
        if (!user.getRole().isAdmin()) {
            throw new SettlementException("관리자만 정산 상태를 변경할 수 있습니다.");
        }
        return user;
    }

    private Settlement findForUpdate(Long settlementId) {
        if (settlementId == null || settlementId <= 0L) {
            throw new SettlementException("정산 ID가 올바르지 않습니다.");
        }
        return settlementRepository.findByIdForUpdate(settlementId)
                .orElseThrow(() -> new SettlementException("정산을 찾을 수 없습니다."));
    }

    private void validateSnapshot(Settlement settlement, SettlementAggregation aggregation) {
        if (!Objects.equals(settlement.getTotalProductSalesAmount(), aggregation.totalProductSalesAmount())
                || !Objects.equals(settlement.getTotalShippingSalesAmount(), aggregation.totalShippingSalesAmount())
                || !Objects.equals(settlement.getTotalCancellationAmount(), aggregation.totalCancellationAmount())
                || !Objects.equals(settlement.getTotalReturnAmount(), aggregation.totalReturnAmount())
                || !Objects.equals(settlement.getTotalCommissionAmount(), aggregation.totalCommissionAmount())
                || !Objects.equals(settlement.getTotalAdjustmentAmount(), aggregation.totalAdjustmentAmount())
                || !Objects.equals(settlement.getSettlementAmount(), aggregation.settlementAmount())
                || !Objects.equals(settlement.getLedgerEntryCount(), aggregation.ledgerEntryCount())) {
            throw new SettlementException("정산 snapshot과 귀속 원장 집계가 일치하지 않습니다.");
        }
    }

    private void validateEntryOwnership(
            Settlement settlement,
            List<SettlementLedgerEntry> entries
    ) {
        for (SettlementLedgerEntry entry : entries) {
            if (entry.getSettlement() == null
                    || !Objects.equals(entry.getSettlement().getId(), settlement.getId())
                    || entry.getSeller() == null || settlement.getSeller() == null
                    || !Objects.equals(entry.getSeller().getId(), settlement.getSeller().getId())) {
                throw new SettlementException("정산 귀속 원장의 소유 관계가 올바르지 않습니다.");
            }
        }
    }
}
