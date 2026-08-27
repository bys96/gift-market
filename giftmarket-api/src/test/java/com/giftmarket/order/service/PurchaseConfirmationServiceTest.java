package com.giftmarket.order.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.ShipmentRepository;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseConfirmationServiceTest {
    @Mock OrderItemRepository items;
    @Mock ShipmentRepository shipments;
    @Mock PurchaseConfirmationQuantities quantities;
    @Mock OrderItem item;
    @Mock Order order;
    @Mock User buyer;
    @Mock SellerOrder sellerOrder;
    @Mock Shipment shipment;
    PurchaseConfirmationService service;

    @BeforeEach void setUp() { service = new PurchaseConfirmationService(items, shipments, quantities); }

    @Test void confirmsAllCurrentlyConfirmableQuantity() {
        ownedDelivered();
        var pending = new PurchaseConfirmationQuantities.PendingQuantities(Map.of(), Map.of(), Map.of());
        given(quantities.load(java.util.List.of(30L))).willReturn(pending);
        given(quantities.confirmable(item, pending)).willReturn(2);
        given(item.getConfirmedQuantity()).willReturn(2);

        var response = service.confirm(1L, 10L, 30L);

        verify(item).confirmPurchase(2);
        assertThat(response.confirmedQuantity()).isEqualTo(2);
        assertThat(response.confirmableQuantity()).isZero();
    }

    @Test void unauthenticatedRequestIsRejected() {
        assertThatThrownBy(() -> service.confirm(null, 10L, 30L))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test void anotherBuyerCannotConfirm() {
        located();
        given(buyer.getId()).willReturn(2L);
        assertThatThrownBy(() -> service.confirm(1L, 10L, 30L)).isInstanceOf(OrderException.class);
        verifyNoInteractions(shipments, quantities);
    }

    @Test void mismatchedOrderAndItemIsRejected() {
        given(items.findByIdAndOrderIdForUpdate(30L, 10L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm(1L, 10L, 30L)).isInstanceOf(OrderException.class);
    }

    @Test void confirmationBeforeDeliveryIsRejected() {
        located();
        given(buyer.getId()).willReturn(1L);
        given(sellerOrder.getId()).willReturn(20L);
        given(shipments.findBySellerOrderIdAndType(20L, ShipmentType.ORIGINAL_OUTBOUND))
                .willReturn(Optional.of(shipment));
        given(shipment.getStatus()).willReturn(ShipmentStatus.SHIPPED);
        assertThatThrownBy(() -> service.confirm(1L, 10L, 30L)).isInstanceOf(OrderException.class);
    }

    @Test void noQuantityBecauseCanceledReturnedPendingOrConfirmedIsRejected() {
        ownedDelivered();
        var pending = new PurchaseConfirmationQuantities.PendingQuantities(Map.of(), Map.of(), Map.of());
        given(quantities.load(java.util.List.of(30L))).willReturn(pending);
        given(quantities.confirmable(item, pending)).willReturn(0);
        assertThatThrownBy(() -> service.confirm(1L, 10L, 30L)).isInstanceOf(OrderException.class);
        verify(item, never()).confirmPurchase(anyInt());
    }

    @Test void domainFailurePropagatesAsOrderExceptionForTransactionRollback() {
        ownedDelivered();
        var pending = new PurchaseConfirmationQuantities.PendingQuantities(Map.of(), Map.of(), Map.of());
        given(quantities.load(java.util.List.of(30L))).willReturn(pending);
        given(quantities.confirmable(item, pending)).willReturn(1);
        doThrow(new IllegalStateException("invalid quantity state")).when(item).confirmPurchase(1);
        assertThatThrownBy(() -> service.confirm(1L, 10L, 30L)).isInstanceOf(OrderException.class);
    }

    private void located() {
        given(items.findByIdAndOrderIdForUpdate(30L, 10L)).willReturn(Optional.of(item));
        given(item.getOrder()).willReturn(order);
        given(order.getUser()).willReturn(buyer);
        given(item.getSellerOrder()).willReturn(sellerOrder);
        given(item.getId()).willReturn(30L);
    }

    private void ownedDelivered() {
        located();
        given(buyer.getId()).willReturn(1L);
        given(sellerOrder.getId()).willReturn(20L);
        given(shipments.findBySellerOrderIdAndType(20L, ShipmentType.ORIGINAL_OUTBOUND))
                .willReturn(Optional.of(shipment));
        given(shipment.getStatus()).willReturn(ShipmentStatus.DELIVERED);
        given(shipment.getDeliveredAt()).willReturn(LocalDateTime.now());
    }
}
