package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import com.giftmarket.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementGenerationService {

    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final SellerRepository sellerRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementLedgerEntryRepository ledgerRepository;
    private final ActiveSettlementClaimService activeClaimService;
    private final SettlementLedgerAggregator ledgerAggregator;

    @Transactional
    public Optional<Settlement> generate(SettlementGenerationCommand command) {
        validateCommand(command);
        Seller seller = sellerRepository.findByIdForUpdate(command.sellerId())
                .orElseThrow(() -> new SettlementException("정산 판매자를 찾을 수 없습니다."));
        validateSellerStatus(seller);
        if (settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                seller.getId(),
                command.periodStart(),
                command.periodEnd()
        )) {
            throw new SettlementException("동일한 판매자와 기간의 정산이 이미 존재합니다.");
        }

        List<SettlementLedgerEntry> candidates = ledgerRepository.findGenerationCandidatesForUpdate(
                seller.getId(),
                command.periodEnd(),
                command.cutoff()
        );
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Set<Long> sellerOrderIds = new LinkedHashSet<>();
        for (SettlementLedgerEntry entry : candidates) {
            validateCandidate(entry, seller, command);
            sellerOrderIds.add(entry.getSellerOrder().getId());
        }
        List<SellerOrder> lockedOrders = sellerOrderRepository.findAllBySellerIdAndIdInForUpdate(
                seller.getId(),
                sellerOrderIds
        );
        if (lockedOrders.size() != sellerOrderIds.size()) {
            throw new SettlementException("정산 대상 판매자 주문을 모두 잠글 수 없습니다.");
        }
        Set<Long> activeClaimOrders = activeClaimService.findActiveSellerOrderIds(sellerOrderIds);
        List<SettlementLedgerEntry> selected = candidates.stream()
                .filter(entry -> !activeClaimOrders.contains(entry.getSellerOrder().getId()))
                .toList();
        if (selected.isEmpty()) {
            return Optional.empty();
        }

        SettlementAggregation aggregation = ledgerAggregator.aggregate(selected);
        Settlement settlement = Settlement.create(
                seller,
                createSettlementNumber(LocalDate.now()),
                command.periodStart(),
                command.periodEnd(),
                aggregation.totalProductSalesAmount(),
                aggregation.totalShippingSalesAmount(),
                aggregation.totalCancellationAmount(),
                aggregation.totalReturnAmount(),
                aggregation.totalCommissionAmount(),
                aggregation.totalAdjustmentAmount(),
                aggregation.ledgerEntryCount()
        );
        for (SettlementLedgerEntry entry : selected) {
            entry.assignTo(settlement);
        }
        settlementRepository.save(settlement);
        if (!Objects.equals(settlement.getSettlementAmount(), aggregation.settlementAmount())) {
            throw new SettlementException("생성된 정산 snapshot과 원장 합계가 일치하지 않습니다.");
        }
        return Optional.of(settlement);
    }

    private void validateCommand(SettlementGenerationCommand command) {
        if (command == null || command.sellerId() == null || command.sellerId() <= 0L
                || command.periodStart() == null || command.periodEnd() == null
                || !command.periodStart().isBefore(command.periodEnd())
                || command.cutoff() == null) {
            throw new SettlementException("정산 생성 조건이 올바르지 않습니다.");
        }
    }

    private void validateSellerStatus(Seller seller) {
        if (seller.getStatus() != SellerStatus.ACTIVE
                && seller.getStatus() != SellerStatus.SALES_SUSPENDED) {
            throw new SettlementException("현재 판매자 상태에서는 정산을 생성할 수 없습니다.");
        }
    }

    private void validateCandidate(
            SettlementLedgerEntry entry,
            Seller seller,
            SettlementGenerationCommand command
    ) {
        if (entry == null || entry.getId() == null || entry.getSettlement() != null
                || entry.getEligibleAt() == null
                || !entry.getEligibleAt().isBefore(command.periodEnd())
                || entry.getEligibleAt().isAfter(command.cutoff())
                || entry.getSeller() == null
                || !Objects.equals(entry.getSeller().getId(), seller.getId())
                || entry.getSellerOrder() == null || entry.getSellerOrder().getId() == null
                || entry.getSellerOrder().getSeller() == null
                || !Objects.equals(entry.getSellerOrder().getSeller().getId(), seller.getId())) {
            throw new SettlementException("정산 대상 원장의 판매자 귀속이 올바르지 않습니다.");
        }
    }

    private String createSettlementNumber(LocalDate date) {
        return "ST-" + NUMBER_DATE.format(date) + "-"
                + UUID.randomUUID().toString().replace("-", "");
    }
}
