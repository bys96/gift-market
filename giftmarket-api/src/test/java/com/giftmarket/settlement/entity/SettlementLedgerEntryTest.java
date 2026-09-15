package com.giftmarket.settlement.entity;

import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettlementLedgerEntryTest {

    @Test
    void validatesAmountSignForEveryLedgerType() {
        assertInvalidSign(SettlementLedgerType.SALE_PRODUCT, -1L, null, null);
        assertInvalidSign(SettlementLedgerType.SALE_SHIPPING, -1L, null, null);
        assertInvalidSign(SettlementLedgerType.CANCELLATION_REFUND, 1L, null, null);
        assertInvalidSign(SettlementLedgerType.RETURN_REFUND, 1L, null, null);
        assertInvalidSign(SettlementLedgerType.COMMISSION, 1L, 1_000, 10_000L);
        assertInvalidSign(SettlementLedgerType.COMMISSION_REVERSAL, -1L, 1_000, 10_000L);
    }

    @Test
    void rejectsZeroAmountEconomicEvent() {
        assertInvalidSign(SettlementLedgerType.SALE_PRODUCT, 0L, null, null);
        assertInvalidSign(SettlementLedgerType.MANUAL_ADJUSTMENT, 0L, null, null);
    }

    @Test
    void acceptsExpectedSignedAmounts() {
        assertThat(create(SettlementLedgerType.SALE_PRODUCT, 10_000L, null, null).getAmount())
                .isEqualTo(10_000L);
        assertThat(create(SettlementLedgerType.CANCELLATION_REFUND, -2_000L, null, null).getAmount())
                .isEqualTo(-2_000L);
        assertThat(create(SettlementLedgerType.COMMISSION, -1_000L, 1_000, 10_000L).getAmount())
                .isEqualTo(-1_000L);
        assertThat(create(SettlementLedgerType.COMMISSION_REVERSAL, 200L, 1_000, 2_000L).getAmount())
                .isEqualTo(200L);
    }

    @Test
    void rejectsSellerOrderOwnershipMismatch() {
        Seller seller = mock(Seller.class);
        Seller otherSeller = mock(Seller.class);
        SellerOrder sellerOrder = mock(SellerOrder.class);
        when(sellerOrder.getSeller()).thenReturn(otherSeller);

        assertThatThrownBy(() -> SettlementLedgerEntry.create(
                seller,
                sellerOrder,
                SettlementLedgerType.SALE_PRODUCT,
                10_000L,
                SettlementLedgerSourceType.SELLER_ORDER,
                1L,
                "SALE_PRODUCT",
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lifecycleAllowsOneEligibilityAndOneSettlementAssignment() {
        Seller seller = mock(Seller.class);
        SellerOrder sellerOrder = sellerOrder(seller);
        SettlementLedgerEntry entry = create(
                seller,
                sellerOrder,
                SettlementLedgerType.SALE_PRODUCT,
                10_000L,
                null,
                null
        );
        LocalDateTime eligibleAt = entry.getOccurredAt().plusDays(7);
        Settlement settlement = settlement(seller, "ST-1");

        entry.activateEligibility(eligibleAt);
        entry.activateEligibility(eligibleAt);
        entry.assignTo(settlement);
        entry.assignTo(settlement);

        assertThat(entry.getEligibleAt()).isEqualTo(eligibleAt);
        assertThat(entry.getSettlement()).isSameAs(settlement);
        assertThatThrownBy(() -> entry.activateEligibility(eligibleAt.plusDays(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> entry.assignTo(settlement(seller, "ST-2")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirmedSettlementPreventsLedgerLifecycleChanges() {
        Seller seller = mock(Seller.class);
        SettlementLedgerEntry entry = create(
                seller,
                sellerOrder(seller),
                SettlementLedgerType.SALE_PRODUCT,
                10_000L,
                null,
                null
        );
        Settlement settlement = settlement(seller, "ST-CONFIRMED");
        entry.assignTo(settlement);
        settlement.confirm(mock(User.class), LocalDateTime.now());

        assertThatThrownBy(() -> entry.activateEligibility(entry.getOccurredAt().plusDays(7)))
                .isInstanceOf(IllegalStateException.class);
    }

    private void assertInvalidSign(
            SettlementLedgerType type,
            long amount,
            Integer rateBps,
            Long baseAmount
    ) {
        assertThatThrownBy(() -> create(type, amount, rateBps, baseAmount))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SettlementLedgerEntry create(
            SettlementLedgerType type,
            long amount,
            Integer rateBps,
            Long baseAmount
    ) {
        Seller seller = mock(Seller.class);
        return create(seller, sellerOrder(seller), type, amount, rateBps, baseAmount);
    }

    private SettlementLedgerEntry create(
            Seller seller,
            SellerOrder sellerOrder,
            SettlementLedgerType type,
            long amount,
            Integer rateBps,
            Long baseAmount
    ) {
        return SettlementLedgerEntry.create(
                seller,
                sellerOrder,
                type,
                amount,
                sourceType(type),
                1L,
                type.name(),
                LocalDateTime.of(2026, 9, 1, 12, 0),
                null,
                rateBps,
                baseAmount,
                null,
                null,
                null
        );
    }

    private SettlementLedgerSourceType sourceType(SettlementLedgerType type) {
        return switch (type) {
            case CANCELLATION_REFUND, RETURN_REFUND, COMMISSION_REVERSAL ->
                    SettlementLedgerSourceType.PAYMENT_CANCELLATION;
            default -> SettlementLedgerSourceType.SELLER_ORDER;
        };
    }

    private SellerOrder sellerOrder(Seller seller) {
        SellerOrder sellerOrder = mock(SellerOrder.class);
        when(sellerOrder.getSeller()).thenReturn(seller);
        return sellerOrder;
    }

    private Settlement settlement(Seller seller, String number) {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        return Settlement.create(
                seller,
                number,
                start,
                start.plusDays(7),
                10_000L,
                0L,
                0L,
                0L,
                1_000L,
                0L,
                2
        );
    }
}
