package com.giftmarket.order.service;

import com.giftmarket.order.dto.response.CancellationRefundCalculation;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderCancellationItemRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderCancellationRefundCalculatorTest {

    private static final long ORDER_ID = 10L;
    private static final long SELLER_ORDER_ID = 20L;
    private static final long CANCELLATION_ID = 30L;

    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderCancellationRepository cancellationRepository;
    @Mock OrderCancellationItemRepository cancellationItemRepository;
    @Mock OrderItemRepository orderItemRepository;

    private OrderCancellationRefundCalculator calculator;
    private Order order;
    private SellerOrder sellerOrder;
    private Payment payment;

    @BeforeEach
    void setUp() {
        calculator = new OrderCancellationRefundCalculator(
                paymentRepository,
                orderRepository,
                sellerOrderRepository,
                cancellationRepository,
                cancellationItemRepository,
                orderItemRepository
        );
        order = paidOrder();
        sellerOrder = paidSellerOrder(order, SELLER_ORDER_ID);
        payment = mock(Payment.class);
        given(payment.getId()).willReturn(5L);
        given(payment.getStatus()).willReturn(PaymentStatus.PAID);
        given(payment.getAmount()).willReturn(1_000_000L);
        given(paymentRepository.findFirstByOrderIdOrderByIdDesc(ORDER_ID))
                .willReturn(Optional.of(payment));
        given(paymentRepository.findByIdForUpdate(5L)).willReturn(Optional.of(payment));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(sellerOrderRepository.findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID))
                .willReturn(Optional.of(sellerOrder));
    }

    @Test
    void calculatesPartialQuantityFromOrderSnapshotWithoutShippingRefund() {
        OrderItem item = item(sellerOrder, 101L, 10_000L, 2_000L, 2, 3_000L);
        OrderCancellation cancellation = requested(item, 1);
        stub(cancellation, List.of(item), 1);

        CancellationRefundCalculation result = calculator.calculate(CANCELLATION_ID);

        assertThat(result.productRefundAmount()).isEqualTo(12_000L);
        assertThat(result.shippingRefundAmount()).isZero();
        assertThat(result.totalRefundAmount()).isEqualTo(12_000L);
        assertThat(result.sellerOrderFullyCanceled()).isFalse();
        assertThat(result.items()).singleElement().satisfies(calculation -> {
            assertThat(calculation.unitRefundAmount()).isEqualTo(12_000L);
            assertThat(calculation.remainingQuantityAfterCancellation()).isEqualTo(1);
        });
    }

    @Test
    void supportsNegativeAdditionalPriceAndRefundsShippingOnFullCancellation() {
        OrderItem item = item(sellerOrder, 101L, 10_000L, -1_000L, 1, 3_000L);
        OrderCancellation cancellation = requested(item, 1);
        stub(cancellation, List.of(item), 1);

        CancellationRefundCalculation result = calculator.calculate(CANCELLATION_ID);

        assertThat(result.productRefundAmount()).isEqualTo(9_000L);
        assertThat(result.shippingRefundAmount()).isEqualTo(3_000L);
        assertThat(result.totalRefundAmount()).isEqualTo(12_000L);
        assertThat(result.sellerOrderFullyCanceled()).isTrue();
    }

    @Test
    void refundsShippingOnlyWhenEverySellerOrderItemHasNoRemainingQuantity() {
        OrderItem first = item(sellerOrder, 101L, 10_000L, 0L, 2, 2_000L);
        first.increaseCanceledQuantity(1);
        OrderItem second = item(sellerOrder, 102L, 20_000L, 0L, 1, 1_000L);
        OrderCancellation cancellation = requested(List.of(first, second), List.of(1, 1));
        stub(cancellation, List.of(first, second), 1, 1);

        CancellationRefundCalculation result = calculator.calculate(CANCELLATION_ID);

        assertThat(result.productRefundAmount()).isEqualTo(30_000L);
        assertThat(result.shippingRefundAmount()).isEqualTo(3_000L);
        assertThat(result.sellerOrderFullyCanceled()).isTrue();
    }

    @Test
    void doesNotRefundShippingWhenAnotherItemStillRemains() {
        OrderItem first = item(sellerOrder, 101L, 10_000L, 0L, 1, 2_000L);
        OrderItem second = item(sellerOrder, 102L, 20_000L, 0L, 1, 1_000L);
        OrderCancellation cancellation = requested(first, 1);
        stub(cancellation, List.of(first, second), 1);

        CancellationRefundCalculation result = calculator.calculate(CANCELLATION_ID);

        assertThat(result.shippingRefundAmount()).isZero();
        assertThat(result.sellerOrderFullyCanceled()).isFalse();
    }

    @Test
    void freeShippingFullCancellationKeepsShippingRefundZero() {
        OrderItem item = item(sellerOrder, 101L, 10_000L, 0L, 1, 0L);
        OrderCancellation cancellation = requested(item, 1);
        stub(cancellation, List.of(item), 1);

        CancellationRefundCalculation result = calculator.calculate(CANCELLATION_ID);

        assertThat(result.shippingRefundAmount()).isZero();
        assertThat(result.sellerOrderFullyCanceled()).isTrue();
    }

    @Test
    void otherPendingCancellationIsNotIncludedInThisCalculation() {
        OrderItem item = item(sellerOrder, 101L, 10_000L, 0L, 2, 3_000L);
        OrderCancellation cancellation = requested(item, 1);
        stub(cancellation, List.of(item), 1);

        CancellationRefundCalculation result = calculator.calculate(CANCELLATION_ID);

        assertThat(result.shippingRefundAmount()).isZero();
        assertThat(result.items().getFirst().remainingQuantityAfterCancellation()).isEqualTo(1);
    }

    @Test
    void processingCancellationCanBeCalculated() {
        OrderItem item = item(sellerOrder, 101L, 10_000L, 0L, 1, 0L);
        OrderCancellation cancellation = requested(item, 1);
        cancellation.startProcessing(LocalDateTime.now());
        stub(cancellation, List.of(item), 1);

        assertThat(calculator.calculate(CANCELLATION_ID).productRefundAmount())
                .isEqualTo(10_000L);
    }

    @Test
    void calculatesOnlyTargetSellerOrderInMultiSellerOrder() {
        SellerOrder otherSellerOrder = paidSellerOrder(order, 21L);
        item(otherSellerOrder, 201L, 90_000L, 0L, 1, 9_000L);
        OrderItem target = item(sellerOrder, 101L, 10_000L, 0L, 1, 3_000L);
        OrderCancellation cancellation = requested(target, 1);
        stub(cancellation, List.of(target), 1);

        CancellationRefundCalculation result = calculator.calculate(CANCELLATION_ID);

        assertThat(result.totalRefundAmount()).isEqualTo(13_000L);
    }

    @Test
    void rejectsQuantityGreaterThanCurrentRemainingQuantity() {
        OrderItem item = item(sellerOrder, 101L, 10_000L, 0L, 2, 0L);
        OrderCancellation cancellation = requested(item, 2);
        stub(cancellation, List.of(item), 2);
        item.increaseCanceledQuantity(1);

        assertThatThrownBy(() -> calculator.calculate(CANCELLATION_ID))
                .isInstanceOf(OrderException.class);
    }

    @Test
    void rejectedFailedAndCompletedCancellationsCannotBeCalculated() {
        for (String terminal : List.of("REJECTED", "FAILED", "COMPLETED")) {
            OrderItem item = item(sellerOrder, 101L, 10_000L, 0L, 1, 0L);
            OrderCancellation cancellation = requested(item, 1);
            if (terminal.equals("REJECTED")) {
                cancellation.reject("reason", LocalDateTime.now());
            } else {
                cancellation.startProcessing(LocalDateTime.now());
                if (terminal.equals("FAILED")) cancellation.fail(LocalDateTime.now());
                else cancellation.complete(LocalDateTime.now());
            }
            stub(cancellation, List.of(item), 1);
            assertThatThrownBy(() -> calculator.calculate(CANCELLATION_ID))
                    .as(terminal)
                    .isInstanceOf(OrderException.class);
        }
    }

    @Test
    void rejectsMultiplicationOverflow() {
        OrderItem item = item(sellerOrder, 101L, Long.MAX_VALUE, 0L, 2, 0L);
        OrderCancellation cancellation = requested(item, 2);
        stub(cancellation, List.of(item), 2);
        given(payment.getAmount()).willReturn(Long.MAX_VALUE);

        assertThatThrownBy(() -> calculator.calculate(CANCELLATION_ID))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("안전하게 계산");
    }

    @Test
    void rejectsAdditionOverflow() {
        OrderItem first = item(sellerOrder, 101L, Long.MAX_VALUE - 10L, 0L, 1, 0L);
        OrderItem second = item(sellerOrder, 102L, 100L, 0L, 1, 0L);
        OrderCancellation cancellation = requested(List.of(first, second), List.of(1, 1));
        stub(cancellation, List.of(first, second), 1, 1);
        given(payment.getAmount()).willReturn(Long.MAX_VALUE);

        assertThatThrownBy(() -> calculator.calculate(CANCELLATION_ID))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("안전하게 계산");
    }

    @Test
    void rejectsRefundGreaterThanOriginalPaymentAmount() {
        OrderItem item = item(sellerOrder, 101L, 10_000L, 0L, 1, 0L);
        OrderCancellation cancellation = requested(item, 1);
        stub(cancellation, List.of(item), 1);
        given(payment.getAmount()).willReturn(9_999L);

        assertThatThrownBy(() -> calculator.calculate(CANCELLATION_ID))
                .isInstanceOf(OrderException.class);
    }

    @Test
    void locksPaymentOrderSellerOrderCancellationAndItemsInOrder() {
        OrderItem item = item(sellerOrder, 101L, 10_000L, 0L, 1, 0L);
        OrderCancellation cancellation = requested(item, 1);
        stub(cancellation, List.of(item), 1);

        calculator.calculate(CANCELLATION_ID);

        InOrder locks = inOrder(
                paymentRepository,
                orderRepository,
                sellerOrderRepository,
                cancellationRepository,
                orderItemRepository
        );
        locks.verify(paymentRepository).findByIdForUpdate(5L);
        locks.verify(orderRepository).findByIdForUpdate(ORDER_ID);
        locks.verify(sellerOrderRepository).findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID);
        locks.verify(cancellationRepository).findByIdForUpdate(CANCELLATION_ID);
        locks.verify(orderItemRepository).findAllBySellerOrderIdForUpdate(SELLER_ORDER_ID);
    }

    private void stub(
            OrderCancellation cancellation,
            List<OrderItem> sellerOrderItems,
            int... quantities
    ) {
        List<OrderCancellationItem> cancellationItems = new java.util.ArrayList<>();
        for (int index = 0; index < quantities.length; index++) {
            cancellationItems.add(OrderCancellationItem.create(
                    cancellation,
                    sellerOrderItems.get(index),
                    quantities[index]
            ));
        }
        given(cancellationRepository.findById(CANCELLATION_ID)).willReturn(Optional.of(cancellation));
        given(cancellationRepository.findByIdForUpdate(CANCELLATION_ID)).willReturn(Optional.of(cancellation));
        given(orderItemRepository.findAllBySellerOrderIdForUpdate(SELLER_ORDER_ID))
                .willReturn(sellerOrderItems);
        given(cancellationItemRepository.findAllByOrderCancellationIdOrderByIdAsc(CANCELLATION_ID))
                .willReturn(cancellationItems);
    }

    private OrderCancellation requested(OrderItem item, int quantity) {
        return requested(List.of(item), List.of(quantity));
    }

    private OrderCancellation requested(List<OrderItem> items, List<Integer> quantities) {
        OrderCancellation cancellation = OrderCancellation.createRequested(
                order, sellerOrder, "key", "reason", LocalDateTime.now()
        );
        ReflectionTestUtils.setField(cancellation, "id", CANCELLATION_ID);
        return cancellation;
    }

    private OrderItem item(
            SellerOrder owner,
            long id,
            long productPrice,
            long additionalPrice,
            int quantity,
            long shippingFee
    ) {
        OrderItem item = OrderItem.create(
                order, mock(Product.class), null, owner.getSeller(), owner,
                null, "snapshot product", null, "store", null, null,
                productPrice, additionalPrice, quantity, shippingFee == 0L, shippingFee
        );
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private Order paidOrder() {
        Order value = Order.createPendingPayment(
                "GM-ORDER", mock(User.class), 1_000_000L, 100_000L,
                "recipient", "010", "12345", "address", null
        );
        ReflectionTestUtils.setField(value, "id", ORDER_ID);
        value.markPaid(LocalDateTime.now());
        return value;
    }

    private SellerOrder paidSellerOrder(Order parent, long id) {
        SellerOrder value = SellerOrder.createPendingPayment(parent, mock(Seller.class));
        ReflectionTestUtils.setField(value, "id", id);
        value.markPaid();
        return value;
    }
}
