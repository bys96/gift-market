package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerSourceType;
import com.giftmarket.settlement.entity.SettlementLedgerType;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SettlementLedgerServiceTest {

    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock SettlementLedgerEntryRepository ledgerRepository;

    private SettlementLedgerService service;
    private Seller seller;
    private SellerOrder sellerOrder;
    private LocalDateTime occurredAt;

    @BeforeEach
    void setUp() {
        service = new SettlementLedgerService(sellerOrderRepository, ledgerRepository);
        seller = mock(Seller.class);
        sellerOrder = mock(SellerOrder.class);
        occurredAt = LocalDateTime.of(2026, 9, 1, 12, 0);
        lenient().when(seller.getId()).thenReturn(10L);
        lenient().when(sellerOrder.getId()).thenReturn(20L);
        when(sellerOrder.getSeller()).thenReturn(seller);
        given(sellerOrderRepository.findByIdAndSellerIdForUpdate(20L, 10L))
                .willReturn(Optional.of(sellerOrder));
    }

    @Test
    void createsProductSaleWithLockedSellerOrderOwnership() {
        SettlementLedgerCommand command = standardCommand(10_000L, 20L, "SALE_PRODUCT");
        given(ledgerRepository.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        SettlementLedgerEntry result = service.recordProductSale(command);

        assertThat(result.getType()).isEqualTo(SettlementLedgerType.SALE_PRODUCT);
        assertThat(result.getAmount()).isEqualTo(10_000L);
        assertThat(result.getSeller()).isSameAs(seller);
        assertThat(result.getSellerOrder()).isSameAs(sellerOrder);
    }

    @Test
    void convertsRefundAndCommissionMagnitudesToNegativeLedgerAmounts() {
        given(ledgerRepository.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        SettlementLedgerCommand refund = standardCommand(2_000L, 30L, "CANCEL");
        SettlementLedgerCommand commission = SettlementLedgerCommand.commission(
                10L,
                20L,
                1_000L,
                20L,
                "COMMISSION",
                occurredAt,
                null,
                1_000,
                10_000L
        );

        SettlementLedgerEntry refundEntry = service.recordCancellationRefund(refund);
        SettlementLedgerEntry commissionEntry = service.recordCommission(commission);

        assertThat(refundEntry.getAmount()).isEqualTo(-2_000L);
        assertThat(commissionEntry.getAmount()).isEqualTo(-1_000L);
    }

    @Test
    void sameSourceAndSamePayloadRetryReturnsExistingEntry() {
        SettlementLedgerCommand command = standardCommand(10_000L, 20L, "SALE_PRODUCT");
        SettlementLedgerEntry existing = existingProductSale(10_000L);
        given(ledgerRepository.findBySourceTypeAndSourceIdAndSourceDetailKey(
                SettlementLedgerSourceType.SELLER_ORDER,
                20L,
                "SALE_PRODUCT"
        )).willReturn(Optional.of(existing));

        SettlementLedgerEntry result = service.recordProductSale(command);

        assertThat(result).isSameAs(existing);
        verify(ledgerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sameSourceAndDifferentPayloadIsRejected() {
        SettlementLedgerCommand command = standardCommand(11_000L, 20L, "SALE_PRODUCT");
        SettlementLedgerEntry existing = existingProductSale(10_000L);
        given(ledgerRepository.findBySourceTypeAndSourceIdAndSourceDetailKey(
                SettlementLedgerSourceType.SELLER_ORDER,
                20L,
                "SALE_PRODUCT"
        )).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.recordProductSale(command))
                .isInstanceOf(SettlementException.class);
        verify(ledgerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsSellerOrderOwnershipMismatch() {
        Seller otherSeller = mock(Seller.class);
        when(otherSeller.getId()).thenReturn(99L);
        when(sellerOrder.getSeller()).thenReturn(otherSeller);

        assertThatThrownBy(() -> service.recordProductSale(
                standardCommand(10_000L, 20L, "SALE_PRODUCT")
        )).isInstanceOf(SettlementException.class);
    }

    @Test
    void rejectsSellerOrderSourceMismatchForSale() {
        assertThatThrownBy(() -> service.recordProductSale(
                standardCommand(10_000L, 999L, "SALE_PRODUCT")
        )).isInstanceOf(SettlementException.class);
    }

    @Test
    void savesCommissionSnapshotWithoutRecalculation() {
        SettlementLedgerCommand command = SettlementLedgerCommand.commission(
                10L,
                20L,
                1_234L,
                20L,
                "COMMISSION",
                occurredAt,
                null,
                1_000,
                12_345L
        );
        ArgumentCaptor<SettlementLedgerEntry> captor = ArgumentCaptor.forClass(
                SettlementLedgerEntry.class
        );

        service.recordCommission(command);

        verify(ledgerRepository).save(captor.capture());
        assertThat(captor.getValue().getCommissionRateBps()).isEqualTo(1_000);
        assertThat(captor.getValue().getCommissionBaseAmount()).isEqualTo(12_345L);
        assertThat(captor.getValue().getAmount()).isEqualTo(-1_234L);
    }

    private SettlementLedgerCommand standardCommand(
            long amount,
            long sourceId,
            String detailKey
    ) {
        return SettlementLedgerCommand.standard(
                10L,
                20L,
                amount,
                sourceId,
                detailKey,
                occurredAt,
                null
        );
    }

    private SettlementLedgerEntry existingProductSale(long amount) {
        return SettlementLedgerEntry.create(
                seller,
                sellerOrder,
                SettlementLedgerType.SALE_PRODUCT,
                amount,
                SettlementLedgerSourceType.SELLER_ORDER,
                20L,
                "SALE_PRODUCT",
                occurredAt,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
