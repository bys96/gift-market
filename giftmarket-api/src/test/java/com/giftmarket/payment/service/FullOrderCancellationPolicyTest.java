package com.giftmarket.payment.service;

import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.payment.exception.PaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FullOrderCancellationPolicyTest {
    @Mock SellerOrderRepository sellers;
    @Mock OrderItemRepository items;
    @Mock OrderCancellationRepository cancellations;
    @Mock ReturnRequestRepository returns;
    @Mock ExchangeRequestRepository exchanges;
    Order order;
    SellerOrder seller;
    OrderItem item;
    FullOrderCancellationPolicy policy;

    @BeforeEach void setup() {
        policy = new FullOrderCancellationPolicy(sellers, items, cancellations, returns, exchanges);
        order = Order.createPendingPayment("test", null, 10000L, 0L, "test", "01012345678", "12345", "test", null);
        ReflectionTestUtils.setField(order, "id", 1L);
        seller = SellerOrder.createPendingPayment(order, null);
        ReflectionTestUtils.setField(seller, "id", 2L);
        seller.markPaid();
        item = mock(OrderItem.class);
        lenient().when(sellers.findAllByOrderIdForUpdate(1L)).thenReturn(List.of(seller));
        lenient().when(items.findAllBySellerOrderIdForUpdate(2L)).thenReturn(List.of(item));
    }

    @Test void paidOrderWithoutClaimsCanStartFullCancellation() {
        assertThatCode(() -> policy.validate(order, SellerOrderStatus.PAID)).doesNotThrowAnyException();
        verify(sellers).findAllByOrderIdForUpdate(1L);
        verify(items).findAllBySellerOrderIdForUpdate(2L);
    }

    @Test void unpaidOrderRequiresEverySellerToBePendingPayment() {
        ReflectionTestUtils.setField(seller, "status", SellerOrderStatus.PENDING_PAYMENT);
        assertThatCode(() -> policy.validate(order, SellerOrderStatus.PENDING_PAYMENT)).doesNotThrowAnyException();
        seller.markPaid();
        assertThatThrownBy(() -> policy.validate(order, SellerOrderStatus.PENDING_PAYMENT)).isInstanceOf(PaymentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SellerOrderStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    void rejectsEveryNonPaidSellerState(SellerOrderStatus state) {
        ReflectionTestUtils.setField(seller, "status", state);
        assertThatThrownBy(() -> policy.validate(order, SellerOrderStatus.PAID)).isInstanceOf(PaymentException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"confirmed", "canceled", "returned", "exchanged"})
    void rejectsConfirmedOrPreviouslyProcessedQuantities(String type) {
        switch (type) {
            case "confirmed" -> when(item.getConfirmedQuantity()).thenReturn(1);
            case "canceled" -> when(item.getCanceledQuantity()).thenReturn(1);
            case "returned" -> when(item.getReturnedQuantity()).thenReturn(1);
            default -> when(item.getExchangedQuantity()).thenReturn(1);
        }
        assertThatThrownBy(() -> policy.validate(order, SellerOrderStatus.PAID)).isInstanceOf(PaymentException.class);
    }

    @ParameterizedTest @EnumSource(value = OrderCancellationStatus.class, names = {"REQUESTED", "PROCESSING"})
    void rejectsActiveCancellation(OrderCancellationStatus state) {
        when(cancellations.existsBySellerOrderIdAndStatusIn(eq(2L), argThat(states -> states.contains(state)))).thenReturn(true);
        assertThatThrownBy(() -> policy.validate(order, SellerOrderStatus.PAID)).isInstanceOf(PaymentException.class);
    }

    @ParameterizedTest @EnumSource(value = ReturnRequestStatus.class, names = {"REQUESTED", "APPROVED", "COLLECTING", "RECEIVED", "INSPECTED", "REFUNDING"})
    void rejectsActiveReturn(ReturnRequestStatus state) {
        when(returns.existsBySellerOrderIdAndStatusIn(eq(2L), argThat(states -> states.contains(state)))).thenReturn(true);
        assertThatThrownBy(() -> policy.validate(order, SellerOrderStatus.PAID)).isInstanceOf(PaymentException.class);
    }

    @ParameterizedTest @EnumSource(value = ExchangeRequestStatus.class, names = {"REQUESTED", "APPROVED", "PAYMENT_PENDING", "COLLECTING", "RECEIVED", "INSPECTED", "RESHIPPING"})
    void rejectsActiveExchange(ExchangeRequestStatus state) {
        when(exchanges.existsBySellerOrderIdAndStatusIn(eq(2L), argThat(states -> states.contains(state)))).thenReturn(true);
        assertThatThrownBy(() -> policy.validate(order, SellerOrderStatus.PAID)).isInstanceOf(PaymentException.class);
    }

    @Test void oneBlockedSellerRejectsTheEntireMultiSellerOrder() {
        var other = SellerOrder.createPendingPayment(order, null);
        ReflectionTestUtils.setField(other, "status", SellerOrderStatus.PREPARING);
        when(sellers.findAllByOrderIdForUpdate(1L)).thenReturn(List.of(seller, other));
        assertThatThrownBy(() -> policy.validate(order, SellerOrderStatus.PAID)).isInstanceOf(PaymentException.class);
    }

    @Test void incompleteSellerOrItemDataFailsClosed() {
        when(items.findAllBySellerOrderIdForUpdate(2L)).thenReturn(List.of());
        assertThatThrownBy(() -> policy.validate(order, SellerOrderStatus.PAID)).isInstanceOf(PaymentException.class);
        when(sellers.findAllByOrderIdForUpdate(1L)).thenReturn(List.of());
        assertThatThrownBy(() -> policy.validate(order, SellerOrderStatus.PAID)).isInstanceOf(PaymentException.class);
    }
}
