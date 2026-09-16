package com.giftmarket.settlement.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerType;
import com.giftmarket.settlement.entity.SettlementStatus;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import com.giftmarket.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerSettlementQueryServiceTest {

    @Mock SellerRepository sellerRepository;
    @Mock SettlementRepository settlementRepository;
    @Mock SettlementLedgerEntryRepository ledgerRepository;

    private SellerSettlementQueryService service;
    private Seller seller;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        service = new SellerSettlementQueryService(sellerRepository, settlementRepository, ledgerRepository);
        seller = mock(Seller.class);
        given(seller.getId()).willReturn(10L);
        given(seller.getStatus()).willReturn(SellerStatus.ACTIVE);
        given(sellerRepository.findByUserId(1L)).willReturn(Optional.of(seller));
        settlement = Settlement.create(
                seller, "ST-20260916-a", LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 9, 16, 0, 0),
                10_000L, 3_000L, 1_000L, 2_000L, 700L, -100L, 6
        );
        ReflectionTestUtils.setField(settlement, "id", 55L);
    }

    @Test
    void listsOnlyOwnSettlementsUsingStoredSnapshotAndPageMetadata() {
        PageRequest request = PageRequest.of(0, 20);
        given(settlementRepository.findAllBySellerIdOrderByPeriodEndDescIdDesc(10L, request))
                .willReturn(new PageImpl<>(List.of(settlement), request, 1));

        var result = service.getSettlements(1L, null, 0, 20);

        assertThat(result.settlements()).hasSize(1);
        assertThat(result.settlements().getFirst().settlementAmount()).isEqualTo(9_200L);
        assertThat(result.settlements().getFirst().ledgerEntryCount()).isEqualTo(6);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(settlementRepository).findAllBySellerIdOrderByPeriodEndDescIdDesc(10L, request);
    }

    @Test
    void statusFilterAndPagingUseSellerScopedLatestFirstQuery() {
        PageRequest request = PageRequest.of(1, 2);
        given(settlementRepository.findAllBySellerIdAndStatusOrderByPeriodEndDescIdDesc(
                10L, SettlementStatus.READY, request))
                .willReturn(new PageImpl<>(List.of(settlement), request, 3));

        var result = service.getSettlements(1L, SettlementStatus.READY, 1, 2);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.settlements()).hasSize(1);
        verify(settlementRepository).findAllBySellerIdAndStatusOrderByPeriodEndDescIdDesc(
                10L, SettlementStatus.READY, request);
    }

    @Test
    void detailIncludesOrderedLedgerWithoutAdminIdentity() {
        SellerOrder sellerOrder = mock(SellerOrder.class);
        given(sellerOrder.getId()).willReturn(77L);
        SettlementLedgerEntry entry = mock(SettlementLedgerEntry.class);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 15, 18, 37);
        given(entry.getId()).willReturn(99L);
        given(entry.getSellerOrder()).willReturn(sellerOrder);
        given(entry.getType()).willReturn(SettlementLedgerType.SALE_PRODUCT);
        given(entry.getAmount()).willReturn(10_000L);
        given(entry.getOccurredAt()).willReturn(occurredAt);
        given(settlementRepository.findByIdAndSellerId(55L, 10L)).willReturn(Optional.of(settlement));
        given(ledgerRepository.findSellerSettlementEntries(55L, 10L)).willReturn(List.of(entry));

        var result = service.getSettlement(1L, 55L);

        assertThat(result.settlement().settlementNumber()).isEqualTo(settlement.getSettlementNumber());
        assertThat(result.ledgerEntries()).hasSize(1);
        assertThat(result.ledgerEntries().getFirst().sellerOrderId()).isEqualTo(77L);
        assertThat(result.ledgerEntries().getFirst().occurredAt()).isEqualTo(occurredAt);
        verify(ledgerRepository).findSellerSettlementEntries(55L, 10L);
    }

    @Test
    void missingOrOtherSellerDetailDoesNotReadLedger() {
        assertThatThrownBy(() -> service.getSettlement(1L, 55L))
                .isInstanceOf(SellerException.class).hasMessageContaining("찾을 수 없습니다");
        verify(ledgerRepository, never()).findSellerSettlementEntries(55L, 10L);
    }

    @Test
    void summarySplitsEligibleHoldAndAwaitingUsingSignedAmounts() {
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        var unassigned = List.of(
                amount(10_000L, past), amount(-3_000L, past),
                amount(5_000L, future), amount(-1_000L, future),
                amount(2_000L, null)
        );
        given(ledgerRepository.findUnassignedAmounts(10L)).willReturn(unassigned);

        var result = service.getSummary(1L);

        assertThat(result.currency()).isEqualTo("KRW");
        assertThat(result.eligibleAmount()).isEqualTo(7_000L);
        assertThat(result.holdAmount()).isEqualTo(4_000L);
        assertThat(result.awaitingEligibilityAmount()).isEqualTo(2_000L);
        assertThat(result.unassignedAmount()).isEqualTo(13_000L);
        verify(ledgerRepository).findUnassignedAmounts(10L);
    }

    @Test
    void emptyResultsAreZeroAndDoNotIncludeAssignedLedger() {
        PageRequest request = PageRequest.of(0, 20);
        given(settlementRepository.findAllBySellerIdOrderByPeriodEndDescIdDesc(10L, request))
                .willReturn(new PageImpl<>(List.of(), request, 0));
        given(ledgerRepository.findUnassignedAmounts(10L)).willReturn(List.of());

        assertThat(service.getSettlements(1L, null, 0, 20).settlements()).isEmpty();
        assertThat(service.getSummary(1L).unassignedAmount()).isZero();
    }

    @Test
    void salesSuspendedSellerCanReadExistingSettlements() {
        given(seller.getStatus()).willReturn(SellerStatus.SALES_SUSPENDED);
        given(ledgerRepository.findUnassignedAmounts(10L)).willReturn(List.of());

        assertThat(service.getSummary(1L).unassignedAmount()).isZero();
    }

    @Test
    void unauthenticatedOrSuspendedSellerCannotRead() {
        assertThatThrownBy(() -> service.getSummary(null)).isInstanceOf(AuthenticationException.class);
        given(seller.getStatus()).willReturn(SellerStatus.SUSPENDED);
        assertThatThrownBy(() -> service.getSummary(1L)).isInstanceOf(SellerException.class);
        verify(ledgerRepository, never()).findUnassignedAmounts(10L);
    }

    @Test
    void invalidPageIsRejected() {
        assertThatThrownBy(() -> service.getSettlements(1L, null, -1, 20))
                .isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.getSettlements(1L, null, 0, 101))
                .isInstanceOf(SellerException.class);
    }

    private SettlementLedgerEntryRepository.UnassignedAmount amount(long value, LocalDateTime eligibleAt) {
        SettlementLedgerEntryRepository.UnassignedAmount projection =
                mock(SettlementLedgerEntryRepository.UnassignedAmount.class);
        given(projection.getAmount()).willReturn(value);
        given(projection.getEligibleAt()).willReturn(eligibleAt);
        return projection;
    }
}
