package com.giftmarket.payment.service;

import com.giftmarket.order.entity.ExchangeReasonType;
import com.giftmarket.order.entity.ExchangeRequest;
import com.giftmarket.order.entity.ExchangeRequestItem;
import com.giftmarket.order.entity.ExchangeRequestStatus;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.repository.ExchangeRequestItemRepository;
import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.dto.response.ExchangeShippingPaymentResponse;
import com.giftmarket.payment.entity.ExchangeShippingPayment;
import com.giftmarket.payment.entity.ExchangeShippingPaymentStatus;
import com.giftmarket.payment.entity.PaymentMethod;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.GatewayConfirmResult;
import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.repository.ExchangeShippingPaymentRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExchangeShippingPaymentTransactionServiceTest {

    private static final long USER_ID = 1L;
    private static final long EXCHANGE_ID = 2L;
    private static final long PAYMENT_ID = 3L;
    private static final long ORDER_ID = 4L;
    private static final long SELLER_ORDER_ID = 5L;
    private static final long ORDER_ITEM_ID = 6L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 30, 12, 0);

    @Mock ExchangeShippingPaymentRepository paymentRepository;
    @Mock ExchangeRequestRepository exchangeRepository;
    @Mock ExchangeRequestItemRepository exchangeItemRepository;
    @Mock OrderRepository orderRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock User user;
    @Mock Seller seller;
    @Mock Product product;

    private ExchangeShippingPaymentTransactionService service;
    private Order order;
    private SellerOrder sellerOrder;
    private OrderItem orderItem;
    private ExchangeRequest exchange;
    private ExchangeRequestItem exchangeItem;
    private ExchangeShippingPayment payment;

    @BeforeEach
    void setUp() {
        service = new ExchangeShippingPaymentTransactionService(
                paymentRepository, exchangeRepository, exchangeItemRepository,
                orderRepository, sellerOrderRepository, orderItemRepository
        ) {
            @Override
            LocalDateTime currentTime() {
                return NOW;
            }
        };
        given(user.getId()).willReturn(USER_ID);
        order = Order.createPendingPayment(
                "GM-ORDER", user, 20_000L, 0L,
                "구매자", "010-1234-5678", "12345", "서울", null
        );
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.markPaid(NOW.minusDays(5));
        sellerOrder = SellerOrder.createPendingPayment(order, seller);
        ReflectionTestUtils.setField(sellerOrder, "id", SELLER_ORDER_ID);
        sellerOrder.markPaid();
        sellerOrder.prepare(NOW.minusDays(4));
        sellerOrder.markShipped(NOW.minusDays(3));
        sellerOrder.markDelivered(NOW.minusDays(2));
        orderItem = OrderItem.create(
                order, product, null, seller, sellerOrder, null,
                "상품", null, "상점", null, null, 10_000L, 0L,
                2, true, 0L, 3_000L, 6_000L
        );
        ReflectionTestUtils.setField(orderItem, "id", ORDER_ITEM_ID);
        setExchange(ExchangeReasonType.CHANGE_OF_MIND);

        given(paymentRepository.findByExchangeRequestIdForUpdate(EXCHANGE_ID))
                .willAnswer(invocation -> Optional.ofNullable(payment));
        given(paymentRepository.save(any(ExchangeShippingPayment.class))).willAnswer(invocation -> {
            payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
            return payment;
        });
        given(exchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willAnswer(invocation -> Optional.of(exchange));
        given(exchangeRepository.findById(EXCHANGE_ID)).willAnswer(invocation -> Optional.of(exchange));
        given(exchangeItemRepository.findAllByExchangeRequestIdOrderByOrderItemIdAsc(EXCHANGE_ID))
                .willAnswer(invocation -> List.of(exchangeItem));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(sellerOrderRepository.findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID))
                .willReturn(Optional.of(sellerOrder));
        given(orderItemRepository.findAllByIdInForUpdate(List.of(ORDER_ITEM_ID)))
                .willReturn(List.of(orderItem));
    }

    @Test
    void prepareCreatesReadyPaymentFromServerShippingFee() {
        ExchangeShippingPaymentResponse response = service.prepare(USER_ID, EXCHANGE_ID);

        assertThat(response.status()).isEqualTo(ExchangeShippingPaymentStatus.READY);
        assertThat(response.amount()).isEqualTo(6_000L);
        assertThat(response.providerOrderId()).isEqualTo("EXCHANGE-SHIPPING-2-1");
        assertThat(response.idempotencyKey()).isEqualTo("EXCHANGE-SHIPPING-PAYMENT-2-1");
        assertThat(payment.getExchangeRequest()).isSameAs(exchange);
        assertThat(exchange.getStatus()).isEqualTo(ExchangeRequestStatus.PAYMENT_PENDING);
        verify(paymentRepository).save(any(ExchangeShippingPayment.class));
    }

    @Test
    void duplicatePrepareReusesExistingPaymentRow() {
        ExchangeShippingPaymentResponse first = service.prepare(USER_ID, EXCHANGE_ID);
        ExchangeShippingPaymentResponse second = service.prepare(USER_ID, EXCHANGE_ID);

        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(second.providerOrderId()).isEqualTo(first.providerOrderId());
        verify(paymentRepository).save(any(ExchangeShippingPayment.class));
    }

    @Test
    void prepareRejectsSellerResponsibleExchange() {
        setExchange(ExchangeReasonType.DEFECTIVE);

        assertThatThrownBy(() -> service.prepare(USER_ID, EXCHANGE_ID))
                .isInstanceOf(PaymentException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void prepareRejectsBuyerExchangeBeforePaymentPending() {
        ReflectionTestUtils.setField(exchange, "status", ExchangeRequestStatus.REQUESTED);

        assertThatThrownBy(() -> service.prepare(USER_ID, EXCHANGE_ID))
                .isInstanceOf(PaymentException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void prepareRejectsBuyerExchangeAfterPaymentWasCompleted() {
        service.prepare(USER_ID, EXCHANGE_ID);
        service.startConfirm(USER_ID, EXCHANGE_ID, confirmRequest());
        given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));
        service.apply(PAYMENT_ID, success());

        ExchangeShippingPaymentResponse response = service.prepare(USER_ID, EXCHANGE_ID);

        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.status()).isEqualTo(ExchangeShippingPaymentStatus.SUCCEEDED);
        assertThat(exchange.getStatus()).isEqualTo(ExchangeRequestStatus.COLLECTING);
    }

    @Test
    void changedConfirmAmountIsRejectedBeforePaymentRequest() {
        service.prepare(USER_ID, EXCHANGE_ID);

        assertThatThrownBy(() -> service.startConfirm(
                USER_ID, EXCHANGE_ID,
                new PaymentConfirmRequest("payment-key", payment.getProviderOrderId(), 1L)
        )).isInstanceOf(PaymentException.class);

        assertThat(payment.getStatus()).isEqualTo(ExchangeShippingPaymentStatus.READY);
        assertThat(payment.getProviderPaymentKey()).isNull();
    }

    @Test
    void successfulConfirmMovesExchangeToCollectingAndKeepsReservation() {
        service.prepare(USER_ID, EXCHANGE_ID);
        service.startConfirm(USER_ID, EXCHANGE_ID, confirmRequest());
        given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));

        ExchangeShippingPaymentResponse response = service.apply(PAYMENT_ID, success());

        assertThat(response.status()).isEqualTo(ExchangeShippingPaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderPaymentKey()).isEqualTo("payment-key");
        assertThat(payment.getProviderStatus()).isEqualTo("DONE");
        assertThat(exchange.getStatus()).isEqualTo(ExchangeRequestStatus.COLLECTING);
        assertThat(exchangeItem.getReservedQuantity()).isEqualTo(1);
        assertThat(exchangeItem.getReleasedQuantity()).isZero();
        assertThat(exchangeItem.getConsumedQuantity()).isZero();
        assertThat(exchangeItem.getEffectiveReservedQuantity()).isEqualTo(1);
    }

    @Test
    void duplicateSuccessfulConfirmReturnsCompletedWithoutChangingReservation() {
        service.prepare(USER_ID, EXCHANGE_ID);
        service.startConfirm(USER_ID, EXCHANGE_ID, confirmRequest());
        given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));
        service.apply(PAYMENT_ID, success());

        ExchangeShippingPaymentStart retry = service.startConfirm(USER_ID, EXCHANGE_ID, confirmRequest());

        assertThat(retry.action()).isEqualTo(ExchangeShippingPaymentStart.Action.COMPLETED);
        assertThat(retry.response().status()).isEqualTo(ExchangeShippingPaymentStatus.SUCCEEDED);
        assertThat(exchange.getStatus()).isEqualTo(ExchangeRequestStatus.COLLECTING);
        assertThat(exchangeItem.getReservedQuantity()).isEqualTo(1);
        assertThat(exchangeItem.getReleasedQuantity()).isZero();
        assertThat(exchangeItem.getConsumedQuantity()).isZero();
    }

    @Test
    void failedConfirmKeepsExchangePendingAndReservation() {
        service.prepare(USER_ID, EXCHANGE_ID);
        service.startConfirm(USER_ID, EXCHANGE_ID, confirmRequest());
        given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));

        service.fail(PAYMENT_ID, "DECLINED", "결제 거절", "ABORTED");

        assertThat(payment.getStatus()).isEqualTo(ExchangeShippingPaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("DECLINED");
        assertThat(payment.getFailureMessage()).isEqualTo("결제 거절");
        assertThat(payment.getProviderStatus()).isEqualTo("ABORTED");
        assertThat(exchange.getStatus()).isEqualTo(ExchangeRequestStatus.PAYMENT_PENDING);
        assertThat(exchangeItem.getEffectiveReservedQuantity()).isEqualTo(1);
        assertThat(exchangeItem.getReleasedQuantity()).isZero();
        assertThat(exchangeItem.getConsumedQuantity()).isZero();
    }

    private void setExchange(ExchangeReasonType reasonType) {
        exchange = ExchangeRequest.createRequested(
                order, sellerOrder, "exchange-key", reasonType, "교환 사유",
                "회수인", "010-1111-2222", "12345", "회수 주소", null,
                "수령인", "010-3333-4444", "54321", "재배송 주소", "101호",
                NOW.minusDays(1)
        );
        ReflectionTestUtils.setField(exchange, "id", EXCHANGE_ID);
        exchangeItem = ExchangeRequestItem.create(
                exchange, orderItem, 1, product, null, "상품", null, 10_000L
        );
        if (reasonType.defaultResponsibility() == com.giftmarket.order.entity.ExchangeResponsibility.BUYER) {
            exchangeItem.reserveTargetStock(1);
            exchange.approve(NOW.minusHours(1));
            exchange.startPaymentPending(NOW.minusHours(1), NOW.plusHours(23));
        }
    }

    private PaymentConfirmRequest confirmRequest() {
        return new PaymentConfirmRequest("payment-key", payment.getProviderOrderId(), payment.getAmount());
    }

    private GatewayConfirmResult success() {
        return new GatewayConfirmResult(
                GatewayPaymentStatus.PAID, "payment-key", "transaction-key",
                payment.getProviderOrderId(), payment.getAmount(), "KRW", PaymentMethod.CARD,
                null, "DONE", NOW.plusMinutes(1)
        );
    }
}
