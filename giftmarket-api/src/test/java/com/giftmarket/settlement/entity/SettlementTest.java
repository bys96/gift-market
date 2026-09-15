package com.giftmarket.settlement.entity;

import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SettlementTest {

    @Test
    void createsReadySettlementWithCalculatedSnapshot() {
        Settlement settlement = createSettlement();

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.READY);
        assertThat(settlement.getCurrency()).isEqualTo("KRW");
        assertThat(settlement.getSettlementAmount()).isEqualTo(86_500L);
        assertThat(settlement.getLedgerEntryCount()).isEqualTo(7);
    }

    @Test
    void followsReadyHoldReadyConfirmFlow() {
        Settlement settlement = createSettlement();
        User admin = mock(User.class);
        LocalDateTime heldAt = LocalDateTime.now();
        LocalDateTime releasedAt = heldAt.plusMinutes(1);
        LocalDateTime confirmedAt = releasedAt.plusMinutes(1);

        settlement.hold("  정산 자료 확인  ", admin, heldAt);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.ON_HOLD);
        assertThat(settlement.getHoldReason()).isEqualTo("정산 자료 확인");

        settlement.releaseHold(admin, releasedAt);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.READY);
        assertThat(settlement.getHoldReleasedAt()).isEqualTo(releasedAt);

        settlement.confirm(admin, confirmedAt);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(settlement.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(settlement.getConfirmedByAdminUser()).isSameAs(admin);
    }

    @Test
    void onHoldCannotBeConfirmedDirectly() {
        Settlement settlement = createSettlement();
        User admin = mock(User.class);
        settlement.hold("확인 필요", admin, LocalDateTime.now());

        assertThatThrownBy(() -> settlement.confirm(admin, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirmedSettlementIsTerminal() {
        Settlement settlement = createSettlement();
        User admin = mock(User.class);
        settlement.confirm(admin, LocalDateTime.now());

        assertThatThrownBy(() -> settlement.confirm(admin, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> settlement.hold("다시 보류", admin, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> settlement.releaseHold(admin, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidPeriod() {
        LocalDateTime start = LocalDateTime.now();

        assertThatThrownBy(() -> Settlement.create(
                mock(Seller.class),
                "ST-202609-1",
                start,
                start,
                1L,
                0L,
                0L,
                0L,
                0L,
                0L,
                1
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSettlementAmountOverflow() {
        LocalDateTime start = LocalDateTime.now();

        assertThatThrownBy(() -> Settlement.create(
                mock(Seller.class),
                "ST-OVERFLOW",
                start,
                start.plusDays(1),
                Long.MAX_VALUE,
                1L,
                0L,
                0L,
                0L,
                0L,
                2
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Settlement createSettlement() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        return Settlement.create(
                mock(Seller.class),
                "  ST-202609-1  ",
                start,
                start.plusDays(7),
                100_000L,
                3_000L,
                5_000L,
                8_000L,
                10_000L,
                6_500L,
                7
        );
    }
}
