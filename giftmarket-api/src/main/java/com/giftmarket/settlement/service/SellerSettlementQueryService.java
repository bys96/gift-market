package com.giftmarket.settlement.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.settlement.dto.response.SellerSettlementDetailResponse;
import com.giftmarket.settlement.dto.response.SellerSettlementLedgerResponse;
import com.giftmarket.settlement.dto.response.SellerSettlementPageResponse;
import com.giftmarket.settlement.dto.response.SellerSettlementResponse;
import com.giftmarket.settlement.dto.response.SellerSettlementSummaryResponse;
import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementStatus;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import com.giftmarket.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SellerSettlementQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SellerRepository sellerRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementLedgerEntryRepository ledgerRepository;

    @Transactional(readOnly = true)
    public SellerSettlementPageResponse getSettlements(
            Long userId, SettlementStatus status, int page, int size
    ) {
        Long sellerId = getSellerId(userId);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new SellerException("페이지 정보를 확인해주세요.");
        }
        Page<Settlement> result = status == null
                ? settlementRepository.findAllBySellerIdOrderByPeriodEndDescIdDesc(
                        sellerId, PageRequest.of(page, size))
                : settlementRepository.findAllBySellerIdAndStatusOrderByPeriodEndDescIdDesc(
                        sellerId, status, PageRequest.of(page, size));
        return new SellerSettlementPageResponse(
                result.getContent().stream().map(SellerSettlementResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast()
        );
    }

    @Transactional(readOnly = true)
    public SellerSettlementDetailResponse getSettlement(Long userId, Long settlementId) {
        Long sellerId = getSellerId(userId);
        Settlement settlement = settlementRepository.findByIdAndSellerId(settlementId, sellerId)
                .orElseThrow(() -> new SellerException("정산 내역을 찾을 수 없습니다."));
        return new SellerSettlementDetailResponse(
                SellerSettlementResponse.from(settlement),
                ledgerRepository.findSellerSettlementEntries(settlementId, sellerId)
                        .stream().map(SellerSettlementLedgerResponse::from).toList()
        );
    }

    @Transactional(readOnly = true)
    public SellerSettlementSummaryResponse getSummary(Long userId) {
        Long sellerId = getSellerId(userId);
        LocalDateTime now = LocalDateTime.now();
        long eligible = 0L;
        long hold = 0L;
        long awaitingEligibility = 0L;
        try {
            for (SettlementLedgerEntryRepository.UnassignedAmount entry
                    : ledgerRepository.findUnassignedAmounts(sellerId)) {
                if (entry.getEligibleAt() == null) {
                    awaitingEligibility = Math.addExact(awaitingEligibility, entry.getAmount());
                } else if (entry.getEligibleAt().isAfter(now)) {
                    hold = Math.addExact(hold, entry.getAmount());
                } else {
                    eligible = Math.addExact(eligible, entry.getAmount());
                }
            }
            long unassigned = Math.addExact(Math.addExact(eligible, hold), awaitingEligibility);
            return new SellerSettlementSummaryResponse(
                    Settlement.CURRENCY_KRW, unassigned, eligible, hold, awaitingEligibility
            );
        } catch (ArithmeticException exception) {
            throw new SettlementException("미정산 원장 금액을 안전하게 집계할 수 없습니다.");
        }
    }

    private Long getSellerId(Long userId) {
        if (userId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }
        Seller seller = sellerRepository.findByUserId(userId)
                .orElseThrow(() -> new SellerException("판매자 정보를 찾을 수 없습니다."));
        if (seller.getStatus() != SellerStatus.ACTIVE
                && seller.getStatus() != SellerStatus.SALES_SUSPENDED) {
            throw new SellerException("현재 판매자 상태에서는 정산 내역을 조회할 수 없습니다.");
        }
        return seller.getId();
    }
}
