package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.entity.Shipment;
import com.giftmarket.order.entity.ShipmentStatus;
import com.giftmarket.order.entity.ShipmentType;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.config.SettlementProperties;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerSourceType;
import com.giftmarket.settlement.entity.SettlementLedgerType;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementEligibilityServiceTest {

    private static final Long SELLER_ID = 10L;
    private static final Long SELLER_ORDER_ID = 20L;
    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 9, 1, 10, 0);
    private static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 9, 15, 14, 0);

    @Mock SettlementLedgerEntryRepository ledgerRepository;
    @Mock Seller seller;
    @Mock SellerOrder sellerOrder;
    @Mock Shipment shipment;

    private SettlementProperties properties;
    private SettlementEligibilityService service;

    @BeforeEach
    void setUp() {
        properties = new SettlementProperties();
        properties.setHoldDays(7);
        service = new SettlementEligibilityService(ledgerRepository, properties);

        lenient().when(seller.getId()).thenReturn(SELLER_ID);
        lenient().when(sellerOrder.getId()).thenReturn(SELLER_ORDER_ID);
        lenient().when(sellerOrder.getSeller()).thenReturn(seller);
        lenient().when(sellerOrder.getStatus()).thenReturn(SellerOrderStatus.DELIVERED);
        lenient().when(sellerOrder.getDeliveredAt()).thenReturn(DELIVERED_AT);
        lenient().when(shipment.getSellerOrder()).thenReturn(sellerOrder);
        lenient().when(shipment.getType()).thenReturn(ShipmentType.ORIGINAL_OUTBOUND);
        lenient().when(shipment.getStatus()).thenReturn(ShipmentStatus.DELIVERED);
        lenient().when(shipment.getDeliveredAt()).thenReturn(DELIVERED_AT);
    }

    @Test
    void originalOutboundActivatesProductShippingAndCommissionAtSameTime() {
        SettlementLedgerEntry product = productSale(1_000);
        SettlementLedgerEntry shipping = shippingSale();
        SettlementLedgerEntry commission = commission();
        givenInitialEntries(List.of(product, shipping, commission));

        service.activateInitialSalesEligibility(sellerOrder, shipment);

        LocalDateTime expected = LocalDateTime.of(2026, 9, 22, 14, 0);
        assertThat(product.getEligibleAt()).isEqualTo(expected);
        assertThat(shipping.getEligibleAt()).isEqualTo(expected);
        assertThat(commission.getEligibleAt()).isEqualTo(expected);
    }

    @Test
    void freeShippingWithoutShippingLedgerIsValid() {
        SettlementLedgerEntry product = productSale(1_000);
        SettlementLedgerEntry commission = commission();
        givenInitialEntries(List.of(product, commission));

        service.activateInitialSalesEligibility(sellerOrder, shipment);

        assertThat(product.getEligibleAt()).isEqualTo(DELIVERED_AT.plusDays(7));
        assertThat(commission.getEligibleAt()).isEqualTo(DELIVERED_AT.plusDays(7));
    }

    @Test
    void zeroCommissionWithoutCommissionLedgerIsValidAndKeepsRateSnapshot() {
        SettlementLedgerEntry product = productSale(0);
        givenInitialEntries(List.of(product));

        service.activateInitialSalesEligibility(sellerOrder, shipment);

        assertThat(product.getCommissionRateBps()).isZero();
        assertThat(product.getEligibleAt()).isEqualTo(DELIVERED_AT.plusDays(7));
    }

    @Test
    void deliveryRetryWithSameTimestampIsNoOp() {
        SettlementLedgerEntry product = productSale(1_000);
        givenInitialEntries(List.of(product));

        service.activateInitialSalesEligibility(sellerOrder, shipment);
        service.activateInitialSalesEligibility(sellerOrder, shipment);

        assertThat(product.getEligibleAt()).isEqualTo(DELIVERED_AT.plusDays(7));
    }

    @Test
    void differentExistingEligibleAtIsRejected() {
        SettlementLedgerEntry product = productSale(1_000);
        product.activateEligibility(DELIVERED_AT.plusDays(8));
        givenInitialEntries(List.of(product));

        assertThatThrownBy(() -> service.activateInitialSalesEligibility(sellerOrder, shipment))
                .isInstanceOf(SettlementException.class);
        assertThat(product.getEligibleAt()).isEqualTo(DELIVERED_AT.plusDays(8));
    }

    @Test
    void missingProductLedgerIsRejectedWhenInitialLedgerExists() {
        givenInitialEntries(List.of(shippingSale()));

        assertThatThrownBy(() -> service.activateInitialSalesEligibility(sellerOrder, shipment))
                .isInstanceOf(SettlementException.class);
    }

    @Test
    void exchangeOutboundDoesNotChangeInitialLedgerEligibility() {
        SettlementLedgerEntry product = productSale(1_000);
        given(shipment.getType()).willReturn(ShipmentType.EXCHANGE_OUTBOUND);

        assertThatThrownBy(() -> service.activateInitialSalesEligibility(sellerOrder, shipment))
                .isInstanceOf(SettlementException.class);
        assertThat(product.getEligibleAt()).isNull();
        verify(ledgerRepository, never()).findInitialSalesForUpdate(
                eq(SELLER_ORDER_ID),
                eq(SettlementLedgerSourceType.SELLER_ORDER),
                eq(SELLER_ORDER_ID),
                anySet()
        );
    }

    @Test
    void zeroHoldDaysUsesDeliveredAtExactly() {
        properties.setHoldDays(0);
        SettlementLedgerEntry product = productSale(1_000);
        givenInitialEntries(List.of(product));

        service.activateInitialSalesEligibility(sellerOrder, shipment);

        assertThat(product.getEligibleAt()).isEqualTo(DELIVERED_AT);
    }

    @Test
    void shipmentAndSellerOrderDeliveredAtMismatchIsRejected() {
        given(sellerOrder.getDeliveredAt()).willReturn(DELIVERED_AT.minusSeconds(1));

        assertThatThrownBy(() -> service.activateInitialSalesEligibility(sellerOrder, shipment))
                .isInstanceOf(SettlementException.class);
        verify(ledgerRepository, never()).findInitialSalesForUpdate(
                eq(SELLER_ORDER_ID),
                eq(SettlementLedgerSourceType.SELLER_ORDER),
                eq(SELLER_ORDER_ID),
                anySet()
        );
    }

    @Test
    void legacyOrderWithoutAnyInitialLedgerIsLeftForBackfill() {
        givenInitialEntries(List.of());
        given(ledgerRepository.existsBySellerOrderIdAndSourceTypeAndSourceId(
                SELLER_ORDER_ID,
                SettlementLedgerSourceType.SELLER_ORDER,
                SELLER_ORDER_ID
        )).willReturn(false);

        service.activateInitialSalesEligibility(sellerOrder, shipment);

        verify(ledgerRepository).existsBySellerOrderIdAndSourceTypeAndSourceId(
                SELLER_ORDER_ID,
                SettlementLedgerSourceType.SELLER_ORDER,
                SELLER_ORDER_ID
        );
    }

    @Test
    void inconsistentCommissionSnapshotIsRejected() {
        SettlementLedgerEntry product = productSale(1_000);
        SettlementLedgerEntry commission = SettlementLedgerEntry.create(
                seller, sellerOrder, SettlementLedgerType.COMMISSION, -500L,
                SettlementLedgerSourceType.SELLER_ORDER, SELLER_ORDER_ID,
                "COMMISSION", APPROVED_AT, null, 500, 10_000L,
                null, null, null
        );
        givenInitialEntries(List.of(product, commission));

        assertThatThrownBy(() -> service.activateInitialSalesEligibility(sellerOrder, shipment))
                .isInstanceOf(SettlementException.class);
    }

    private void givenInitialEntries(List<SettlementLedgerEntry> entries) {
        given(ledgerRepository.findInitialSalesForUpdate(
                eq(SELLER_ORDER_ID),
                eq(SettlementLedgerSourceType.SELLER_ORDER),
                eq(SELLER_ORDER_ID),
                eq(Set.of(
                        SettlementLedgerType.SALE_PRODUCT,
                        SettlementLedgerType.SALE_SHIPPING,
                        SettlementLedgerType.COMMISSION
                ))
        )).willReturn(entries);
    }

    private SettlementLedgerEntry productSale(int rateBps) {
        return SettlementLedgerEntry.create(
                seller, sellerOrder, SettlementLedgerType.SALE_PRODUCT, 10_000L,
                SettlementLedgerSourceType.SELLER_ORDER, SELLER_ORDER_ID,
                "SALE_PRODUCT", APPROVED_AT, null, rateBps, 10_000L,
                null, null, null
        );
    }

    private SettlementLedgerEntry shippingSale() {
        return SettlementLedgerEntry.create(
                seller, sellerOrder, SettlementLedgerType.SALE_SHIPPING, 3_000L,
                SettlementLedgerSourceType.SELLER_ORDER, SELLER_ORDER_ID,
                "SALE_SHIPPING", APPROVED_AT, null, null, null,
                null, null, null
        );
    }

    private SettlementLedgerEntry commission() {
        return SettlementLedgerEntry.create(
                seller, sellerOrder, SettlementLedgerType.COMMISSION, -1_000L,
                SettlementLedgerSourceType.SELLER_ORDER, SELLER_ORDER_ID,
                "COMMISSION", APPROVED_AT, null, 1_000, 10_000L,
                null, null, null
        );
    }
}
