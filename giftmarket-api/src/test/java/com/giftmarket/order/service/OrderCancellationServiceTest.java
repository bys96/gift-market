package com.giftmarket.order.service;

import com.giftmarket.order.dto.request.OrderCancellationCreateRequest;
import com.giftmarket.order.dto.request.OrderCancellationItemRequest;
import com.giftmarket.order.dto.response.OrderCancellationResponse;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderCancellationItemRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.PendingCancellationQuantityProjection;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderCancellationServiceTest {

    private static final long USER_ID = 1L;
    private static final long ORDER_ID = 10L;
    private static final long SELLER_ORDER_ID = 20L;
    private static final long ORDER_ITEM_ID = 30L;

    @Mock OrderRepository orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock OrderCancellationRepository cancellationRepository;
    @Mock OrderCancellationItemRepository cancellationItemRepository;

    private OrderCancellationService service;
    private User user;
    private Order order;
    private SellerOrder sellerOrder;
    private OrderItem orderItem;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new OrderCancellationService(
                orderRepository,
                paymentRepository,
                sellerOrderRepository,
                orderItemRepository,
                cancellationRepository,
                cancellationItemRepository
        );
        user = mock(User.class);
        given(user.getId()).willReturn(USER_ID);
        order = paidOrder(user);
        payment = mock(Payment.class);
        given(payment.getId()).willReturn(5L);
        given(payment.getStatus()).willReturn(PaymentStatus.PAID);
        given(payment.isRefundableState()).willReturn(true);
        sellerOrder = paidSellerOrder(order);
        orderItem = orderItem(order, sellerOrder, ORDER_ITEM_ID, 3);

        given(cancellationRepository.findByClientRequestKey(any()))
                .willReturn(Optional.empty());
        given(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID))
                .willReturn(Optional.of(order));
        given(paymentRepository.findFirstByOrderIdAndOrderUserIdOrderByIdDesc(ORDER_ID, USER_ID))
                .willReturn(Optional.of(payment));
        given(paymentRepository.findByIdAndOrderUserIdForUpdate(5L, USER_ID))
                .willReturn(Optional.of(payment));
        given(sellerOrderRepository.findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID))
                .willReturn(Optional.of(sellerOrder));
        given(orderItemRepository.findAllByIdInForUpdate(List.of(ORDER_ITEM_ID)))
                .willReturn(List.of(orderItem));
        given(cancellationRepository.sumItemQuantitiesByStatuses(any(), any()))
                .willReturn(List.of());
        given(cancellationRepository.saveAndFlush(any(OrderCancellation.class)))
                .willAnswer(invocation -> {
                    OrderCancellation cancellation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(cancellation, "id", 100L);
                    return cancellation;
                });
        given(cancellationItemRepository.saveAll(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsRequestedCancellationWithoutChangingOrderItemQuantities() {
        OrderCancellationResponse response = service.create(
                USER_ID,
                ORDER_ID,
                request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 2))
        );

        assertThat(response.status()).isEqualTo(OrderCancellationStatus.REQUESTED);
        assertThat(response.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.orderItemId()).isEqualTo(ORDER_ITEM_ID);
                    assertThat(item.requestedQuantity()).isEqualTo(2);
                });
        assertThat(orderItem.getQuantity()).isEqualTo(3);
        assertThat(orderItem.getCanceledQuantity()).isZero();
    }

    @Test
    void preparingSellerOrderAlsoCreatesRequestedCancellation() {
        sellerOrder.prepare(java.time.LocalDateTime.now());

        OrderCancellationResponse response = service.create(
                USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 1))
        );

        assertThat(response.status()).isEqualTo(OrderCancellationStatus.REQUESTED);
        org.mockito.ArgumentCaptor<OrderCancellation> cancellation =
                org.mockito.ArgumentCaptor.forClass(OrderCancellation.class);
        verify(cancellationRepository).saveAndFlush(cancellation.capture());
        assertThat(cancellation.getValue().isRequiresSellerApproval()).isTrue();
    }

    @Test
    void paidSellerOrderCreatesImmediateFlowCancellation() {
        service.create(
                USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 1))
        );

        org.mockito.ArgumentCaptor<OrderCancellation> cancellation =
                org.mockito.ArgumentCaptor.forClass(OrderCancellation.class);
        verify(cancellationRepository).saveAndFlush(cancellation.capture());
        assertThat(cancellation.getValue().isRequiresSellerApproval()).isFalse();
    }

    @Test
    void createsOneCancellationWithMultipleItems() {
        OrderItem second = orderItem(order, sellerOrder, 31L, 2);
        given(orderItemRepository.findAllByIdInForUpdate(List.of(ORDER_ITEM_ID, 31L)))
                .willReturn(List.of(orderItem, second));

        OrderCancellationResponse response = service.create(
                USER_ID,
                ORDER_ID,
                request(
                        SELLER_ORDER_ID,
                        item(ORDER_ITEM_ID, 1),
                        item(31L, 2)
                )
        );

        assertThat(response.items()).hasSize(2);
        verify(cancellationItemRepository).saveAll(anyList());
    }

    @Test
    void rejectsAnotherUsersOrder() {
        given(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 1))
        )).isInstanceOf(OrderException.class);

        verify(cancellationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsOrderWhosePaymentIsAlreadyCanceling() {
        given(payment.getStatus()).willReturn(PaymentStatus.CANCELING);
        given(payment.isRefundableState()).willReturn(false);

        assertThatThrownBy(() -> service.create(
                USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 1))
        )).isInstanceOf(OrderException.class);

        verify(orderRepository, never()).findByIdAndUserIdForUpdate(any(), any());
        verify(cancellationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSellerOrderFromAnotherOrder() {
        given(sellerOrderRepository.findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 1))
        )).isInstanceOf(OrderException.class);

        verify(cancellationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsOrderItemFromAnotherSellerOrder() {
        SellerOrder other = paidSellerOrder(order);
        ReflectionTestUtils.setField(other, "id", 999L);
        OrderItem otherItem = orderItem(order, other, ORDER_ITEM_ID, 3);
        given(orderItemRepository.findAllByIdInForUpdate(List.of(ORDER_ITEM_ID)))
                .willReturn(List.of(otherItem));

        assertThatThrownBy(() -> service.create(
                USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 1))
        )).isInstanceOf(OrderException.class);
    }

    @Test
    void rejectedAndFailedDoNotHoldQuantityButRequestedAndProcessingDo() {
        PendingCancellationQuantityProjection projection = mock(PendingCancellationQuantityProjection.class);
        given(projection.getOrderItemId()).willReturn(ORDER_ITEM_ID);
        given(projection.getPendingQuantity()).willReturn(2L);
        given(cancellationRepository.sumItemQuantitiesByStatuses(
                any(),
                anySet()
        )).willReturn(List.of(projection));

        assertThatThrownBy(() -> service.create(
                USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 2))
        )).isInstanceOf(OrderException.class);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Set<OrderCancellationStatus>> statuses =
                org.mockito.ArgumentCaptor.forClass(Set.class);
        verify(cancellationRepository).sumItemQuantitiesByStatuses(any(), statuses.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                OrderCancellationStatus.REQUESTED,
                OrderCancellationStatus.PROCESSING
        );
    }

    @Test
    void confirmedAndPendingQuantitiesAreBothSubtracted() {
        ReflectionTestUtils.setField(orderItem, "canceledQuantity", 1);
        PendingCancellationQuantityProjection projection = mock(PendingCancellationQuantityProjection.class);
        given(projection.getOrderItemId()).willReturn(ORDER_ITEM_ID);
        given(projection.getPendingQuantity()).willReturn(1L);
        given(cancellationRepository.sumItemQuantitiesByStatuses(any(), any()))
                .willReturn(List.of(projection));

        assertThatThrownBy(() -> service.create(
                USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 2))
        )).isInstanceOf(OrderException.class);
    }

    @Test
    void duplicateOrderItemInRequestIsRejectedBeforePersistence() {
        assertThatThrownBy(() -> service.create(
                USER_ID,
                ORDER_ID,
                request(
                        SELLER_ORDER_ID,
                        item(ORDER_ITEM_ID, 1),
                        item(ORDER_ITEM_ID, 1)
                )
        )).isInstanceOf(OrderException.class);

        verify(orderRepository, never()).findByIdAndUserIdForUpdate(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 4})
    void invalidOrExcessQuantityIsRejected(int quantity) {
        assertThatThrownBy(() -> service.create(
                USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, quantity))
        )).isInstanceOf(OrderException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SellerOrderStatus.class, names = {
            "PENDING_PAYMENT", "SHIPPED", "DELIVERED", "CANCELLED"
    })
    void nonCancellableSellerOrderStatusIsRejected(SellerOrderStatus status) {
        ReflectionTestUtils.setField(sellerOrder, "status", status);

        assertThatThrownBy(() -> service.create(
                USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 1))
        )).isInstanceOf(OrderException.class);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {
            "ORDERED", "PENDING_PAYMENT", "PAYMENT_FAILED", "PAYMENT_EXPIRED", "CANCELLED"
    })
    void nonPaidOrderStatusIsRejected(OrderStatus status) {
        ReflectionTestUtils.setField(order, "status", status);

        assertThatThrownBy(() -> service.create(
                USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 1))
        )).isInstanceOf(OrderException.class);
    }

    @Test
    void identicalRequestKeyReturnsExistingCancellation() {
        String requestKey = UUID.randomUUID().toString();
        OrderCancellation existing = OrderCancellation.createRequested(
                order, sellerOrder, requestKey, "단순 변심", java.time.LocalDateTime.now()
        );
        ReflectionTestUtils.setField(existing, "id", 100L);
        OrderCancellationItem existingItem = OrderCancellationItem.create(existing, orderItem, 1);
        given(cancellationRepository.findByClientRequestKey(requestKey))
                .willReturn(Optional.of(existing));
        given(cancellationItemRepository.findAllByOrderCancellationIdOrderByIdAsc(100L))
                .willReturn(List.of(existingItem));

        OrderCancellationResponse response = service.create(
                USER_ID,
                ORDER_ID,
                new OrderCancellationCreateRequest(
                        requestKey,
                        SELLER_ORDER_ID,
                        "단순 변심",
                        List.of(item(ORDER_ITEM_ID, 1))
                )
        );

        assertThat(response.cancellationId()).isEqualTo(100L);
        verify(orderRepository, never()).findByIdAndUserIdForUpdate(any(), any());
        verify(cancellationRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentGlobalRequestKeyCollisionIsReturnedAsSafeRequestError() {
        given(cancellationRepository.saveAndFlush(any(OrderCancellation.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.create(
                USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 1))
        )).isInstanceOf(OrderException.class)
                .hasMessageContaining("이미 사용된");

        verify(cancellationItemRepository, never()).saveAll(anyList());
    }

    @Test
    void requestKeyOwnedByAnotherUserIsRejectedWithoutInformationLeak() {
        User otherUser = mock(User.class);
        given(otherUser.getId()).willReturn(999L);
        Order otherOrder = paidOrder(otherUser);
        SellerOrder otherSellerOrder = paidSellerOrder(otherOrder);
        String requestKey = UUID.randomUUID().toString();
        OrderCancellation existing = OrderCancellation.createRequested(
                otherOrder, otherSellerOrder, requestKey, "사유", java.time.LocalDateTime.now()
        );
        given(cancellationRepository.findByClientRequestKey(requestKey))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(
                USER_ID,
                ORDER_ID,
                new OrderCancellationCreateRequest(
                        requestKey,
                        SELLER_ORDER_ID,
                        "단순 변심",
                        List.of(item(ORDER_ITEM_ID, 1))
                )
        )).isInstanceOf(OrderException.class);
    }

    @Test
    void acquiresLocksInOrderSellerOrderAndItemOrder() {
        service.create(USER_ID, ORDER_ID, request(SELLER_ORDER_ID, item(ORDER_ITEM_ID, 1)));

        InOrder lockOrder = inOrder(
                paymentRepository,
                orderRepository,
                sellerOrderRepository,
                orderItemRepository
        );
        lockOrder.verify(paymentRepository).findByIdAndOrderUserIdForUpdate(5L, USER_ID);
        lockOrder.verify(orderRepository).findByIdAndUserIdForUpdate(ORDER_ID, USER_ID);
        lockOrder.verify(sellerOrderRepository)
                .findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID);
        lockOrder.verify(orderItemRepository).findAllByIdInForUpdate(List.of(ORDER_ITEM_ID));
    }

    private OrderCancellationCreateRequest request(
            long sellerOrderId,
            OrderCancellationItemRequest... items
    ) {
        return new OrderCancellationCreateRequest(
                UUID.randomUUID().toString(),
                sellerOrderId,
                " 단순 변심 ",
                List.of(items)
        );
    }

    private OrderCancellationItemRequest item(long orderItemId, int quantity) {
        return new OrderCancellationItemRequest(orderItemId, quantity);
    }

    private Order paidOrder(User owner) {
        Order value = Order.createPendingPayment(
                "GM-ORDER", owner, 30_000L, 0L,
                "수령인", "010-1234-5678", "12345", "서울", null
        );
        ReflectionTestUtils.setField(value, "id", ORDER_ID);
        value.markPaid(java.time.LocalDateTime.now());
        return value;
    }

    private SellerOrder paidSellerOrder(Order parent) {
        SellerOrder value = SellerOrder.createPendingPayment(parent, mock(Seller.class));
        ReflectionTestUtils.setField(value, "id", SELLER_ORDER_ID);
        value.markPaid();
        return value;
    }

    private OrderItem orderItem(
            Order parent,
            SellerOrder sellerOrder,
            long id,
            int quantity
    ) {
        OrderItem value = OrderItem.create(
                parent,
                mock(Product.class),
                null,
                mock(Seller.class),
                sellerOrder,
                null,
                "상품",
                null,
                "상점",
                null,
                null,
                10_000L,
                0L,
                quantity,
                true,
                0L
        );
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }
}
