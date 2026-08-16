package com.giftmarket.payment.service;

import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.cart.repository.CartItemRepository;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentMethod;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.GatewayConfirmResult;
import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 2L;
    private static final Long PAYMENT_ID = 3L;

    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock User user;
    @Mock Product product;
    @Mock Seller seller;

    private PaymentTransactionService service;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new PaymentTransactionService(
                paymentRepository,
                orderRepository,
                orderItemRepository,
                cartItemRepository
        );
        order = Order.createPendingPayment(
                "GM-ORDER", user, 10_000L, 0L,
                "받는 사람", "010-1234-5678", "12345", "서울", null
        );
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        payment = Payment.createReady(
                order, PaymentProvider.TOSS, "GM-PAY", "client-key",
                "confirm-key", 10_000L, "KRW", LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(30)
        );
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        lenient().when(paymentRepository.findByIdAndOrderUserIdForUpdate(
                PAYMENT_ID, USER_ID
        )).thenReturn(Optional.of(payment));
        lenient().when(orderRepository.findByIdAndUserIdForUpdate(
                ORDER_ID, USER_ID
        )).thenReturn(Optional.of(order));
    }

    @Test
    void readyBecomesConfirmingAndThenPaid() {
        PaymentConfirmStart start = service.startConfirm(
                USER_ID, PAYMENT_ID, request(10_000L, "GM-PAY")
        );
        LocalDateTime approvedAt = LocalDateTime.now();
        service.complete(USER_ID, PAYMENT_ID, success(approvedAt));

        assertThat(start.action()).isEqualTo(PaymentConfirmStart.Action.CONFIRM);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getProviderPaymentKey()).isEqualTo("provider-key");
        assertThat(payment.getProviderTransactionId()).isEqualTo("transaction-key");
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(payment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getOrderedAt()).isEqualTo(approvedAt);
    }

    @Test
    void rejectsChangedAmountBeforeConfirming() {
        assertThatThrownBy(() -> service.startConfirm(
                USER_ID, PAYMENT_ID, request(1L, "GM-PAY")
        )).isInstanceOf(PaymentException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void rejectsChangedMerchantPaymentIdBeforeConfirming() {
        assertThatThrownBy(() -> service.startConfirm(
                USER_ID, PAYMENT_ID, request(10_000L, "OTHER")
        )).isInstanceOf(PaymentException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void rejectsPaymentOwnedByAnotherUser() {
        given(paymentRepository.findByIdAndOrderUserIdForUpdate(PAYMENT_ID, 99L))
                .willReturn(Optional.empty());
        assertThatThrownBy(() -> service.startConfirm(
                99L, PAYMENT_ID, request(10_000L, "GM-PAY")
        )).isInstanceOf(PaymentException.class);
    }

    @Test
    void deletesOnlyUnchangedSourceCartItem() {
        given(product.getId()).willReturn(10L);
        OrderItem item = orderItem(100L, 2);
        CartItem cartItem = CartItem.create(user, product, null, 2);
        given(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID))
                .willReturn(List.of(item));
        given(cartItemRepository.findByIdAndUserId(100L, USER_ID))
                .willReturn(Optional.of(cartItem));

        service.startConfirm(USER_ID, PAYMENT_ID, request(10_000L, "GM-PAY"));
        service.complete(USER_ID, PAYMENT_ID, success(LocalDateTime.now()));

        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void keepsChangedSourceCartItem() {
        given(product.getId()).willReturn(10L);
        OrderItem item = orderItem(100L, 2);
        CartItem changed = CartItem.create(user, product, null, 3);
        given(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID))
                .willReturn(List.of(item));
        given(cartItemRepository.findByIdAndUserId(100L, USER_ID))
                .willReturn(Optional.of(changed));

        service.startConfirm(USER_ID, PAYMENT_ID, request(10_000L, "GM-PAY"));
        service.complete(USER_ID, PAYMENT_ID, success(LocalDateTime.now()));

        verify(cartItemRepository, never()).delete(changed);
    }

    @Test
    void permitsAlreadyDeletedSourceAndDirectOrder() {
        given(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID))
                .willReturn(List.of(orderItem(100L, 2), orderItem(null, 2)));
        given(cartItemRepository.findByIdAndUserId(100L, USER_ID))
                .willReturn(Optional.empty());

        service.startConfirm(USER_ID, PAYMENT_ID, request(10_000L, "GM-PAY"));
        service.complete(USER_ID, PAYMENT_ID, success(LocalDateTime.now()));

        verify(cartItemRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private PaymentConfirmRequest request(Long amount, String merchantId) {
        return new PaymentConfirmRequest("provider-key", merchantId, amount);
    }

    private GatewayConfirmResult success(LocalDateTime approvedAt) {
        return new GatewayConfirmResult(
                GatewayPaymentStatus.PAID, "provider-key", "transaction-key",
                "GM-PAY", 10_000L, "KRW", PaymentMethod.CARD, null,
                "DONE", approvedAt
        );
    }

    private OrderItem orderItem(Long sourceCartItemId, int quantity) {
        return OrderItem.create(
                order, product, null, seller, sourceCartItemId,
                "상품", null, "스토어", null, null,
                5_000L, 0L, quantity, true, 0L
        );
    }
}
