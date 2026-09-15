package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementStatus;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import com.giftmarket.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementGenerationServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 9, 30, 23, 0);

    @Mock SellerRepository sellerRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock SettlementRepository settlementRepository;
    @Mock SettlementLedgerEntryRepository ledgerRepository;
    @Mock ActiveSettlementClaimService activeClaimService;
    @Mock SettlementLedgerAggregator ledgerAggregator;
    @Mock Seller seller;

    private SettlementGenerationService service;

    @BeforeEach
    void setUp() {
        service = new SettlementGenerationService(
                sellerRepository,
                sellerOrderRepository,
                settlementRepository,
                ledgerRepository,
                activeClaimService,
                ledgerAggregator
        );
        given(seller.getId()).willReturn(SELLER_ID);
        given(seller.getStatus()).willReturn(SellerStatus.ACTIVE);
        given(sellerRepository.findByIdForUpdate(SELLER_ID)).willReturn(Optional.of(seller));
        given(activeClaimService.findActiveSellerOrderIds(org.mockito.ArgumentMatchers.anyCollection()))
                .willReturn(Set.of());
        given(ledgerAggregator.aggregate(org.mockito.ArgumentMatchers.anyList())).willReturn(aggregation());
        given(settlementRepository.save(org.mockito.ArgumentMatchers.any(Settlement.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsReadySettlementAndAssignsSelectedLedgers() {
        Candidate candidate = candidate(10L, 100L, seller);
        candidates(candidate);

        Settlement result = service.generate(command()).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(SettlementStatus.READY);
        assertThat(result.getSettlementAmount()).isEqualTo(65_500L);
        assertThat(result.getLedgerEntryCount()).isEqualTo(7);
        assertThat(result.getSettlementNumber()).matches("ST-\\d{8}-[0-9a-f]{32}");
        verify(candidate.entry()).assignTo(result);
    }

    @Test
    void emptyCandidatesReturnNoOpWithoutEmptySettlement() {
        given(ledgerRepository.findGenerationCandidatesForUpdate(SELLER_ID, END, CUTOFF))
                .willReturn(List.of());

        assertThat(service.generate(command())).isEmpty();
        verify(settlementRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void activeClaimExcludesWholeSellerOrder() {
        Candidate heldFirst = candidate(10L, 100L, seller);
        Candidate heldSecond = candidate(11L, 100L, seller);
        Candidate available = candidate(12L, 200L, seller);
        candidates(heldFirst, heldSecond, available);
        given(activeClaimService.findActiveSellerOrderIds(Set.of(100L, 200L)))
                .willReturn(Set.of(100L));

        Settlement settlement = service.generate(command()).orElseThrow();

        verify(heldFirst.entry(), never()).assignTo(settlement);
        verify(heldSecond.entry(), never()).assignTo(settlement);
        verify(available.entry()).assignTo(settlement);
    }

    @Test
    void allActiveClaimsReturnNoOp() {
        Candidate candidate = candidate(10L, 100L, seller);
        candidates(candidate);
        given(activeClaimService.findActiveSellerOrderIds(Set.of(100L))).willReturn(Set.of(100L));

        assertThat(service.generate(command())).isEmpty();
        verify(settlementRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void catchesUpAllUnassignedHistoryBeforePeriodEndAndCutoff() {
        Candidate candidate = candidate(10L, 100L, seller);
        candidates(candidate);

        service.generate(command());

        verify(ledgerRepository).findGenerationCandidatesForUpdate(SELLER_ID, END, CUTOFF);
    }

    @Test
    void rejectsDuplicateSellerPeriodBeforeSelectingLedgers() {
        given(settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(SELLER_ID, START, END))
                .willReturn(true);

        assertThatThrownBy(() -> service.generate(command()))
                .isInstanceOf(SettlementException.class);
        verify(ledgerRepository, never()).findGenerationCandidatesForUpdate(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void readyCompositionDoesNotAcceptLaterGenerateForSamePeriod() {
        Candidate candidate = candidate(10L, 100L, seller);
        candidates(candidate);
        service.generate(command());
        given(settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(SELLER_ID, START, END))
                .willReturn(true);

        assertThatThrownBy(() -> service.generate(command()))
                .isInstanceOf(SettlementException.class);
        verify(candidate.entry()).assignTo(org.mockito.ArgumentMatchers.any(Settlement.class));
    }

    @Test
    void salesSuspendedSellerCanGenerateExistingTradeSettlement() {
        given(seller.getStatus()).willReturn(SellerStatus.SALES_SUSPENDED);
        candidates(candidate(10L, 100L, seller));

        assertThat(service.generate(command())).isPresent();
    }

    @Test
    void suspendedAndWithdrawnSellersCannotGenerate() {
        given(seller.getStatus()).willReturn(SellerStatus.SUSPENDED);
        assertThatThrownBy(() -> service.generate(command())).isInstanceOf(SettlementException.class);

        given(seller.getStatus()).willReturn(SellerStatus.WITHDRAWN);
        assertThatThrownBy(() -> service.generate(command())).isInstanceOf(SettlementException.class);
    }

    @Test
    void rejectsCandidateOwnedByAnotherSeller() {
        Seller other = org.mockito.Mockito.mock(Seller.class);
        given(other.getId()).willReturn(99L);
        Candidate candidate = candidate(10L, 100L, other);
        given(ledgerRepository.findGenerationCandidatesForUpdate(SELLER_ID, END, CUTOFF))
                .willReturn(List.of(candidate.entry()));

        assertThatThrownBy(() -> service.generate(command())).isInstanceOf(SettlementException.class);
    }

    @Test
    void rejectsInvalidPeriod() {
        SettlementGenerationCommand invalid = new SettlementGenerationCommand(
                SELLER_ID,
                END,
                START,
                CUTOFF
        );

        assertThatThrownBy(() -> service.generate(invalid)).isInstanceOf(SettlementException.class);
    }

    private void candidates(Candidate... candidates) {
        List<SettlementLedgerEntry> entries = java.util.Arrays.stream(candidates)
                .map(Candidate::entry)
                .toList();
        java.util.Map<Long, SellerOrder> ordersById = new java.util.LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            ordersById.putIfAbsent(candidate.sellerOrder().getId(), candidate.sellerOrder());
        }
        given(ledgerRepository.findGenerationCandidatesForUpdate(SELLER_ID, END, CUTOFF))
                .willReturn(entries);
        given(sellerOrderRepository.findAllBySellerIdAndIdInForUpdate(
                org.mockito.ArgumentMatchers.eq(SELLER_ID),
                org.mockito.ArgumentMatchers.anyCollection()
        )).willReturn(List.copyOf(ordersById.values()));
    }

    private Candidate candidate(long entryId, long sellerOrderId, Seller owner) {
        SellerOrder sellerOrder = org.mockito.Mockito.mock(SellerOrder.class);
        given(sellerOrder.getId()).willReturn(sellerOrderId);
        given(sellerOrder.getSeller()).willReturn(owner);
        SettlementLedgerEntry entry = org.mockito.Mockito.mock(SettlementLedgerEntry.class);
        given(entry.getId()).willReturn(entryId);
        given(entry.getSeller()).willReturn(owner);
        given(entry.getSellerOrder()).willReturn(sellerOrder);
        given(entry.getEligibleAt()).willReturn(START.minusDays(10));
        return new Candidate(entry, sellerOrder);
    }

    private SettlementGenerationCommand command() {
        return new SettlementGenerationCommand(SELLER_ID, START, END, CUTOFF);
    }

    private SettlementAggregation aggregation() {
        return new SettlementAggregation(
                100_000L, 3_000L, 20_000L, 10_000L,
                7_000L, -500L, 65_500L, 7
        );
    }

    private record Candidate(SettlementLedgerEntry entry, SellerOrder sellerOrder) { }
}
