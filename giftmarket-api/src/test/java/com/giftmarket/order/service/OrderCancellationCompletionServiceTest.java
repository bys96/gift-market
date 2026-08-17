package com.giftmarket.order.service;

import com.giftmarket.order.dto.response.OrderCancellationCompletionResult;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderCancellationCompletionServiceTest {

    private static final long ORDER_ID = 10L;
    private static final long SELLER_ORDER_ID = 20L;
    private static final long CANCELLATION_ID = 30L;

    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderCancellationRepository cancellationRepository;
    @Mock OrderCancellationItemRepository cancellationItemRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock OrderInventoryService inventoryService;

    private OrderCancellationCompletionService service;
    private Order order;
    private SellerOrder sellerOrder;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new OrderCancellationCompletionService(
                paymentRepository, orderRepository, sellerOrderRepository,
                cancellationRepository, cancellationItemRepository,
                orderItemRepository, inventoryService
        );
        order = paidOrder();
        sellerOrder = sellerOrder(order, SELLER_ORDER_ID);
        payment = mock(Payment.class);
        given(payment.getId()).willReturn(5L);
        given(payment.getStatus()).willReturn(PaymentStatus.PAID);
        given(paymentRepository.findFirstByOrderIdOrderByIdDesc(ORDER_ID))
                .willReturn(Optional.of(payment));
        given(paymentRepository.findByIdForUpdate(5L)).willReturn(Optional.of(payment));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(sellerOrderRepository.findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID))
                .willReturn(Optional.of(sellerOrder));
    }

    @Test
    void completesProcessingCancellationAndRestoresOnlyRequestedQuantity() {
        OrderItem item = item(sellerOrder, 101L, 2);
        OrderCancellation cancellation = processing(item, 1);
        List<OrderCancellationItem> cancellationItems = stub(cancellation, List.of(item), 1);

        OrderCancellationCompletionResult result = service.complete(CANCELLATION_ID);

        assertThat(result.status()).isEqualTo(OrderCancellationStatus.COMPLETED);
        assertThat(cancellation.getCompletedAt()).isNotNull();
        assertThat(item.getCanceledQuantity()).isEqualTo(1);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.PAID);
        verify(inventoryService).restoreCancellationItems(cancellationItems);
    }

    @Test
    void keepsPreparingSellerOrderWhenAnyQuantityRemains() {
        sellerOrder.prepare(LocalDateTime.now());
        OrderItem item = item(sellerOrder, 101L, 2);
        OrderCancellation cancellation = processing(item, 1);
        stub(cancellation, List.of(item), 1);

        service.complete(CANCELLATION_ID);

        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.PREPARING);
    }

    @Test
    void cancelsSellerOrderOnlyWhenAllItemsAreFullyCanceled() {
        OrderItem first = item(sellerOrder, 101L, 2);
        first.confirmCancellation(1);
        OrderItem second = item(sellerOrder, 102L, 1);
        OrderCancellation cancellation = processing(List.of(first, second), List.of(1, 1));
        stub(cancellation, List.of(first, second), 1, 1);

        service.complete(CANCELLATION_ID);

        assertThat(first.isFullyCanceled()).isTrue();
        assertThat(second.isFullyCanceled()).isTrue();
        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(com.giftmarket.order.entity.OrderStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void completionIsIdempotentAfterCompletedBarrier() {
        OrderItem item = item(sellerOrder, 101L, 2);
        OrderCancellation cancellation = processing(item, 1);
        List<OrderCancellationItem> cancellationItems = stub(cancellation, List.of(item), 1);

        service.complete(CANCELLATION_ID);
        service.complete(CANCELLATION_ID);

        assertThat(item.getCanceledQuantity()).isEqualTo(1);
        verify(inventoryService).restoreCancellationItems(cancellationItems);
    }

    @Test
    void requestedRejectedAndFailedCannotComplete() {
        OrderItem item = item(sellerOrder, 101L, 1);
        OrderCancellation requested = requested(item, 1);
        stub(requested, List.of(item), 1);
        assertThatThrownBy(() -> service.complete(CANCELLATION_ID)).isInstanceOf(OrderException.class);

        OrderCancellation rejected = requested(item, 1);
        rejected.reject("reason", LocalDateTime.now());
        stub(rejected, List.of(item), 1);
        assertThatThrownBy(() -> service.complete(CANCELLATION_ID)).isInstanceOf(OrderException.class);

        OrderCancellation failed = processing(item, 1);
        failed.fail(LocalDateTime.now());
        stub(failed, List.of(item), 1);
        assertThatThrownBy(() -> service.complete(CANCELLATION_ID)).isInstanceOf(OrderException.class);

        verify(inventoryService, never()).restoreCancellationItems(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void blocksCompletionWhileFullPaymentCancellationIsInProgress() {
        given(payment.getStatus()).willReturn(PaymentStatus.CANCELING);
        OrderItem item = item(sellerOrder, 101L, 1);
        OrderCancellation cancellation = processing(item, 1);
        stub(cancellation, List.of(item), 1);

        assertThatThrownBy(() -> service.complete(CANCELLATION_ID))
                .isInstanceOf(OrderException.class);
        assertThat(item.getCanceledQuantity()).isZero();
        verify(inventoryService, never()).restoreCancellationItems(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void validatesAllQuantitiesBeforeRestoringInventory() {
        OrderItem first = item(sellerOrder, 101L, 1);
        OrderItem second = item(sellerOrder, 102L, 1);
        OrderCancellation cancellation = processing(List.of(first, second), List.of(1, 1));
        List<OrderCancellationItem> items = stub(cancellation, List.of(first, second), 1, 1);
        second.confirmCancellation(1);

        assertThatThrownBy(() -> service.complete(CANCELLATION_ID))
                .isInstanceOf(OrderException.class);
        verify(inventoryService, never()).restoreCancellationItems(items);
        assertThat(cancellation.getStatus()).isEqualTo(OrderCancellationStatus.PROCESSING);
    }

    @Test
    void inventoryFailureLeavesDomainStateUncompletedForTransactionRollback() {
        OrderItem item = item(sellerOrder, 101L, 1);
        OrderCancellation cancellation = processing(item, 1);
        List<OrderCancellationItem> items = stub(cancellation, List.of(item), 1);
        doThrow(new OrderException("stock failure"))
                .when(inventoryService).restoreCancellationItems(items);

        assertThatThrownBy(() -> service.complete(CANCELLATION_ID))
                .isInstanceOf(OrderException.class);

        assertThat(item.getCanceledQuantity()).isZero();
        assertThat(cancellation.getStatus()).isEqualTo(OrderCancellationStatus.PROCESSING);
        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.PAID);
    }

    @Test
    void usesExistingPaymentOrderSellerCancellationItemLockOrder() {
        OrderItem item = item(sellerOrder, 101L, 1);
        OrderCancellation cancellation = processing(item, 1);
        stub(cancellation, List.of(item), 1);

        service.complete(CANCELLATION_ID);

        InOrder locks = inOrder(
                paymentRepository, orderRepository, sellerOrderRepository,
                cancellationRepository, orderItemRepository
        );
        locks.verify(paymentRepository).findByIdForUpdate(5L);
        locks.verify(orderRepository).findByIdForUpdate(ORDER_ID);
        locks.verify(sellerOrderRepository).findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID);
        locks.verify(cancellationRepository).findByIdForUpdate(CANCELLATION_ID);
        locks.verify(orderItemRepository).findAllBySellerOrderIdForUpdate(SELLER_ORDER_ID);
    }

    private List<OrderCancellationItem> stub(
            OrderCancellation cancellation,
            List<OrderItem> orderItems,
            int... quantities
    ) {
        List<OrderCancellationItem> items = new java.util.ArrayList<>();
        for (int index = 0; index < quantities.length; index++) {
            items.add(OrderCancellationItem.create(cancellation, orderItems.get(index), quantities[index]));
        }
        given(cancellationRepository.findById(CANCELLATION_ID)).willReturn(Optional.of(cancellation));
        given(cancellationRepository.findByIdForUpdate(CANCELLATION_ID)).willReturn(Optional.of(cancellation));
        given(orderItemRepository.findAllBySellerOrderIdForUpdate(SELLER_ORDER_ID)).willReturn(orderItems);
        given(cancellationItemRepository.findAllByOrderCancellationIdOrderByIdAsc(CANCELLATION_ID))
                .willReturn(items);
        return items;
    }

    private OrderCancellation processing(OrderItem item, int quantity) {
        return processing(List.of(item), List.of(quantity));
    }

    private OrderCancellation processing(List<OrderItem> items, List<Integer> quantities) {
        OrderCancellation cancellation = requested(items.getFirst(), quantities.getFirst());
        cancellation.startProcessing(LocalDateTime.now());
        return cancellation;
    }

    private OrderCancellation requested(OrderItem item, int quantity) {
        OrderCancellation cancellation = OrderCancellation.createRequested(
                order, sellerOrder, "key-" + System.nanoTime(), "reason", LocalDateTime.now()
        );
        ReflectionTestUtils.setField(cancellation, "id", CANCELLATION_ID);
        return cancellation;
    }

    private OrderItem item(SellerOrder owner, long id, int quantity) {
        OrderItem item = OrderItem.create(
                order, mock(Product.class), null, owner.getSeller(), owner,
                null, "product", null, "store", null, null,
                10_000L, 0L, quantity, true, 0L
        );
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private Order paidOrder() {
        Order value = Order.createPendingPayment(
                "GM-ORDER", mock(User.class), 100_000L, 0L,
                "recipient", "010", "12345", "address", null
        );
        ReflectionTestUtils.setField(value, "id", ORDER_ID);
        value.markPaid(LocalDateTime.now());
        return value;
    }

    private SellerOrder sellerOrder(Order parent, long id) {
        SellerOrder value = SellerOrder.createPendingPayment(parent, mock(Seller.class));
        ReflectionTestUtils.setField(value, "id", id);
        value.markPaid();
        return value;
    }
}
