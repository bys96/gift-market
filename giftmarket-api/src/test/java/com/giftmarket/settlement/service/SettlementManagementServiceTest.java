package com.giftmarket.settlement.service;

import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementStatus;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import com.giftmarket.settlement.repository.SettlementRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementManagementServiceTest {

    @Mock SettlementRepository settlementRepository;
    @Mock SettlementLedgerEntryRepository ledgerRepository;
    @Mock UserRepository userRepository;
    @Mock SettlementLedgerAggregator ledgerAggregator;
    @Mock Seller seller;
    @Mock User admin;
    @Mock SettlementLedgerEntry entry;

    private SettlementManagementService service;
    private Settlement settlement;
    private SettlementAggregation aggregation;

    @BeforeEach
    void setUp() {
        service = new SettlementManagementService(
                settlementRepository,
                ledgerRepository,
                userRepository,
                ledgerAggregator
        );
        aggregation = new SettlementAggregation(
                100_000L, 3_000L, 20_000L, 10_000L,
                7_000L, -500L, 65_500L, 7
        );
        settlement = Settlement.create(
                seller,
                "ST-20260915-0123456789abcdef0123456789abcdef",
                LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0),
                aggregation.totalProductSalesAmount(),
                aggregation.totalShippingSalesAmount(),
                aggregation.totalCancellationAmount(),
                aggregation.totalReturnAmount(),
                aggregation.totalCommissionAmount(),
                aggregation.totalAdjustmentAmount(),
                aggregation.ledgerEntryCount()
        );
        ReflectionTestUtils.setField(settlement, "id", 10L);
        given(seller.getId()).willReturn(20L);
        given(entry.getSettlement()).willReturn(settlement);
        given(entry.getSeller()).willReturn(seller);
        given(admin.getRole()).willReturn(UserRole.ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));
        given(settlementRepository.findByIdForUpdate(10L)).willReturn(Optional.of(settlement));
        given(ledgerRepository.findAllBySettlementIdForUpdate(10L)).willReturn(List.of(entry));
        given(ledgerAggregator.aggregate(List.of(entry))).willReturn(aggregation);
    }

    @Test
    void readyCanBeHeldWithAudit() {
        Settlement result = service.hold(10L, 1L, "  자료 확인  ");

        assertThat(result.getStatus()).isEqualTo(SettlementStatus.ON_HOLD);
        assertThat(result.getHoldReason()).isEqualTo("자료 확인");
        assertThat(result.getHeldByAdminUser()).isSameAs(admin);
        assertThat(result.getHeldAt()).isNotNull();
    }

    @Test
    void blankHoldReasonIsRejectedWithoutChangingEntity() {
        assertThatThrownBy(() -> service.hold(10L, 1L, "  "))
                .isInstanceOf(SettlementException.class);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.READY);
    }

    @Test
    void onHoldCanBeReleasedToReadyWithAudit() {
        service.hold(10L, 1L, "확인");

        Settlement result = service.releaseHold(10L, 1L);

        assertThat(result.getStatus()).isEqualTo(SettlementStatus.READY);
        assertThat(result.getHoldReleasedByAdminUser()).isSameAs(admin);
        assertThat(result.getHoldReleasedAt()).isNotNull();
    }

    @Test
    void readyCanBeConfirmedAfterSnapshotVerification() {
        Settlement result = service.confirm(10L, 1L);

        assertThat(result.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(result.getConfirmedByAdminUser()).isSameAs(admin);
        assertThat(result.getConfirmedAt()).isNotNull();
        verify(ledgerRepository).findAllBySettlementIdForUpdate(10L);
    }

    @Test
    void onHoldCannotBeConfirmedDirectly() {
        service.hold(10L, 1L, "확인");

        assertThatThrownBy(() -> service.confirm(10L, 1L))
                .isInstanceOf(SettlementException.class);
    }

    @Test
    void confirmedIsTerminal() {
        service.confirm(10L, 1L);

        assertThatThrownBy(() -> service.hold(10L, 1L, "재보류"))
                .isInstanceOf(SettlementException.class);
        assertThatThrownBy(() -> service.releaseHold(10L, 1L))
                .isInstanceOf(SettlementException.class);
        assertThatThrownBy(() -> service.confirm(10L, 1L))
                .isInstanceOf(SettlementException.class);
    }

    @Test
    void nonAdminCannotHoldOrConfirm() {
        User user = org.mockito.Mockito.mock(User.class);
        given(user.getRole()).willReturn(UserRole.USER);
        given(userRepository.findById(2L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.hold(10L, 2L, "확인"))
                .isInstanceOf(SettlementException.class);
        assertThatThrownBy(() -> service.confirm(10L, 2L))
                .isInstanceOf(SettlementException.class);
        verify(settlementRepository, never()).findByIdForUpdate(10L);
    }

    @Test
    void aggregateMismatchBlocksConfirmation() {
        given(ledgerAggregator.aggregate(List.of(entry))).willReturn(new SettlementAggregation(
                100_000L, 3_000L, 20_000L, 10_000L,
                7_000L, 0L, 66_000L, 7
        ));

        assertThatThrownBy(() -> service.confirm(10L, 1L))
                .isInstanceOf(SettlementException.class);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.READY);
    }

    @Test
    void missingLedgerBlocksConfirmation() {
        given(ledgerAggregator.aggregate(List.of(entry))).willReturn(new SettlementAggregation(
                100_000L, 3_000L, 20_000L, 10_000L,
                7_000L, -500L, 65_500L, 6
        ));

        assertThatThrownBy(() -> service.confirm(10L, 1L))
                .isInstanceOf(SettlementException.class);
    }
}
