package com.giftmarket.payment.service;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.service.OrderInventoryService;
import com.giftmarket.order.service.SellerOrderLifecycleService;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentExpirationTransactionServiceTest {

    private static final Long ORDER_ID = 10L;
    private static final Long PAYMENT_ID = 20L;

    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderInventoryService orderInventoryService;
    @Mock SellerOrderLifecycleService sellerOrderLifecycleService;
    @Mock User user;

    private PaymentExpirationTransactionService service;
    private Order order;
    private Payment payment;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        service = new PaymentExpirationTransactionService(
                paymentRepository,
                orderRepository,
                orderInventoryService,
                sellerOrderLifecycleService
        );
        now = LocalDateTime.of(2026, 8, 16, 12, 0);
        order = Order.createPendingPayment(
                "GM-ORDER", user, 10_000L, 0L,
                "받는 사람", "010-1234-5678", "12345", "서울", null
        );
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        payment = readyPayment(now.minusSeconds(1));
        given(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .willReturn(Optional.of(payment));
    }

    @Test
    void expiresReadyPaymentAndOrderAndRestoresInventory() {
        given(orderRepository.findByIdForUpdate(ORDER_ID))
                .willReturn(Optional.of(order));

        assertThat(service.expireReadyPayment(PAYMENT_ID, now)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_EXPIRED);
        verify(orderInventoryService).restore(ORDER_ID);
        verify(sellerOrderLifecycleService).cancel(ORDER_ID);
    }

    @Test
    void repeatedExpirationRestoresInventoryOnlyOnce() {
        given(orderRepository.findByIdForUpdate(ORDER_ID))
                .willReturn(Optional.of(order));

        assertThat(service.expireReadyPayment(PAYMENT_ID, now)).isTrue();
        assertThat(service.expireReadyPayment(PAYMENT_ID, now)).isFalse();

        verify(orderInventoryService, times(1)).restore(ORDER_ID);
    }

    @Test
    void doesNotExpirePaidConfirmingOrUnexpiredPayment() {
        for (PaymentStatus status : new PaymentStatus[]{
                PaymentStatus.PAID,
                PaymentStatus.CONFIRMING
        }) {
            ReflectionTestUtils.setField(payment, "status", status);
            assertThat(service.expireReadyPayment(PAYMENT_ID, now)).isFalse();
        }

        payment = readyPayment(now.plusSeconds(1));
        given(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .willReturn(Optional.of(payment));
        assertThat(service.expireReadyPayment(PAYMENT_ID, now)).isFalse();

        verify(orderRepository, never()).findByIdForUpdate(ORDER_ID);
        verify(orderInventoryService, never()).restore(ORDER_ID);
    }

    @Test
    void confirmingWonRaceSoExpirationDoesNothing() {
        payment.startConfirm("provider-key", now.minusSeconds(1));

        assertThat(service.expireReadyPayment(PAYMENT_ID, now)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(orderInventoryService, never()).restore(ORDER_ID);
    }

    @Test
    void inventoryFailureLeavesStatusesUnchanged() {
        given(orderRepository.findByIdForUpdate(ORDER_ID))
                .willReturn(Optional.of(order));
        given(orderInventoryService.restore(ORDER_ID))
                .willThrow(new IllegalStateException("restore failed"));

        assertThatThrownBy(() -> service.expireReadyPayment(PAYMENT_ID, now))
                .isInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    private Payment readyPayment(LocalDateTime expiresAt) {
        Payment result = Payment.createReady(
                order,
                PaymentProvider.TOSS,
                "GM-PAY",
                "client-key",
                "confirm-key",
                10_000L,
                "KRW",
                now.minusMinutes(30),
                expiresAt
        );
        ReflectionTestUtils.setField(result, "id", PAYMENT_ID);
        return result;
    }
}
