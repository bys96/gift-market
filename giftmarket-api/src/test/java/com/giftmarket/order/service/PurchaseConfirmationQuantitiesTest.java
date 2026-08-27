package com.giftmarket.order.service;

import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.repository.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PurchaseConfirmationQuantitiesTest {
    private final PurchaseConfirmationQuantities service = new PurchaseConfirmationQuantities(
            mock(OrderCancellationRepository.class), mock(ReturnRequestRepository.class),
            mock(ExchangeRequestRepository.class));

    @Test void partialCancellationAndReturnLeaveOwnedQuantityConfirmable() {
        OrderItem item = item(3, 1, 1, 0, 0);
        assertThat(service.confirmable(item, empty())).isEqualTo(1);
    }

    @Test void completedExchangeRemainsConfirmable() {
        OrderItem item = item(2, 0, 0, 1, 0);
        assertThat(service.confirmable(item, empty())).isEqualTo(2);
    }

    @Test void pendingCancellationReturnAndExchangeAreEachExcluded() {
        OrderItem item = item(5, 0, 0, 0, 0);
        var pending = new PurchaseConfirmationQuantities.PendingQuantities(
                Map.of(30L, 1L), Map.of(30L, 1L), Map.of(30L, 1L));
        assertThat(service.confirmable(item, pending)).isEqualTo(2);
    }

    @Test void alreadyConfirmedQuantityIsExcluded() {
        assertThat(service.confirmable(item(3, 0, 0, 0, 2), empty())).isEqualTo(1);
    }

    private OrderItem item(int quantity, int canceled, int returned, int exchanged, int confirmed) {
        OrderItem item = mock(OrderItem.class);
        given(item.getId()).willReturn(30L);
        given(item.getQuantity()).willReturn(quantity);
        given(item.getCanceledQuantity()).willReturn(canceled);
        given(item.getReturnedQuantity()).willReturn(returned);
        given(item.getExchangedQuantity()).willReturn(exchanged);
        given(item.getConfirmedQuantity()).willReturn(confirmed);
        return item;
    }

    private PurchaseConfirmationQuantities.PendingQuantities empty() {
        return new PurchaseConfirmationQuantities.PendingQuantities(Map.of(), Map.of(), Map.of());
    }
}
