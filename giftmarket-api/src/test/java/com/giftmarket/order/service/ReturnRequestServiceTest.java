package com.giftmarket.order.service;

import com.giftmarket.order.dto.request.ReturnRequestCreateRequest;
import com.giftmarket.order.dto.request.ReturnRequestItemRequest;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.*;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnRequestServiceTest {

    private static final long USER_ID = 1L;
    private static final long ORDER_ID = 10L;
    private static final long SELLER_ORDER_ID = 20L;
    private static final long ORDER_ITEM_ID = 30L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 21, 12, 0);

    @Mock OrderRepository orderRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ShipmentRepository shipmentRepository;
    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock ReturnRequestItemRepository returnRequestItemRepository;

    private ReturnRequestService service;
    private User user;
    private Order order;
    private SellerOrder sellerOrder;
    private OrderItem orderItem;
    private Shipment shipment;

    @BeforeEach
    void setUp() {
        service = spy(new ReturnRequestService(
                orderRepository, sellerOrderRepository, orderItemRepository,
                shipmentRepository, returnRequestRepository, returnRequestItemRepository
        ));
        doReturn(NOW).when(service).currentTime();
        user = mock(User.class);
        given(user.getId()).willReturn(USER_ID);
        order = paidOrder(user);
        sellerOrder = deliveredSellerOrder(order);
        orderItem = orderItem(order, sellerOrder, ORDER_ITEM_ID, 3);
        shipment = deliveredShipment(sellerOrder, NOW.minusDays(1));

        given(returnRequestRepository.findByClientRequestKey(anyString())).willReturn(Optional.empty());
        given(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID)).willReturn(Optional.of(order));
        given(sellerOrderRepository.findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID))
                .willReturn(Optional.of(sellerOrder));
        given(orderItemRepository.findAllByIdInForUpdate(List.of(ORDER_ITEM_ID)))
                .willReturn(List.of(orderItem));
        given(shipmentRepository.findBySellerOrderIdAndType(SELLER_ORDER_ID, ShipmentType.ORIGINAL_OUTBOUND))
                .willReturn(Optional.of(shipment));
        given(returnRequestRepository.sumItemQuantitiesByStatuses(any(), any())).willReturn(List.of());
        given(returnRequestRepository.saveAndFlush(any(ReturnRequest.class))).willAnswer(invocation -> {
            ReturnRequest value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 100L);
            return value;
        });
        given(returnRequestItemRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsBuyerReturnWithoutChangingReturnedQuantityOrShipment() {
        ReturnRequestResponse response = service.create(
                USER_ID, ORDER_ID, SELLER_ORDER_ID,
                request(ReturnReasonType.CHANGE_OF_MIND, item(ORDER_ITEM_ID, 2))
        );

        assertThat(response.status()).isEqualTo(ReturnRequestStatus.REQUESTED);
        assertThat(response.responsibility()).isEqualTo(ReturnResponsibility.BUYER);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.orderItemId()).isEqualTo(ORDER_ITEM_ID);
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.productName()).isEqualTo("상품");
        });
        assertThat(orderItem.getReturnedQuantity()).isZero();
        assertThat(response.approvedAt()).isNull();
    }

    @Test
    void createsMultipleItemsAndSellerResponsibility() {
        OrderItem second = orderItem(order, sellerOrder, 31L, 2);
        given(orderItemRepository.findAllByIdInForUpdate(List.of(ORDER_ITEM_ID, 31L)))
                .willReturn(List.of(orderItem, second));

        ReturnRequestResponse response = service.create(
                USER_ID, ORDER_ID, SELLER_ORDER_ID,
                request(ReturnReasonType.DEFECTIVE, item(31L, 1), item(ORDER_ITEM_ID, 1))
        );

        assertThat(response.responsibility()).isEqualTo(ReturnResponsibility.SELLER);
        assertThat(response.items()).hasSize(2);
        verify(orderItemRepository).findAllByIdInForUpdate(List.of(ORDER_ITEM_ID, 31L));
    }

    @Test
    void otherKeepsNullResponsibilityAndDoesNotApplyBuyerDeadline() {
        shipment = deliveredShipment(sellerOrder, NOW.minusYears(1));
        given(shipmentRepository.findBySellerOrderIdAndType(anyLong(), any())).willReturn(Optional.of(shipment));

        ReturnRequestResponse response = service.create(
                USER_ID, ORDER_ID, SELLER_ORDER_ID,
                request(ReturnReasonType.OTHER, item(ORDER_ITEM_ID, 1))
        );

        assertThat(response.responsibility()).isNull();
    }

    @Test
    void listsAndGetsOnlyOwnedReturnsWithItems() {
        ReturnRequest existing = existingRequest(request(ReturnReasonType.DEFECTIVE, item(ORDER_ITEM_ID, 1)));
        ReturnRequestItem existingItem = ReturnRequestItem.create(existing, orderItem, 1);
        given(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).willReturn(Optional.of(order));
        given(returnRequestRepository.findAllByOrderIdOrderByRequestedAtDescIdDesc(ORDER_ID))
                .willReturn(List.of(existing));
        given(returnRequestItemRepository.findAllByReturnRequestIdInOrderByReturnRequestIdAscOrderItemIdAsc(List.of(100L)))
                .willReturn(List.of(existingItem));
        given(returnRequestRepository.findById(100L)).willReturn(Optional.of(existing));
        given(returnRequestItemRepository.findAllByReturnRequestIdOrderByIdAsc(100L))
                .willReturn(List.of(existingItem));

        assertThat(service.getAllOwned(USER_ID, ORDER_ID)).singleElement()
                .extracting(ReturnRequestResponse::returnRequestId).isEqualTo(100L);
        assertThat(service.getOwned(USER_ID, 100L).items()).hasSize(1);
    }

    @Test
    void rejectsAnotherUsersOrderAndSellerOrderFromAnotherOrder() {
        given(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> createDefault()).isInstanceOf(OrderException.class);

        given(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID)).willReturn(Optional.of(order));
        given(sellerOrderRepository.findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID))
                .willReturn(Optional.empty());
        assertThatThrownBy(() -> createDefault()).isInstanceOf(OrderException.class);
    }

    @Test
    void rejectsItemFromAnotherSellerOrder() {
        SellerOrder other = deliveredSellerOrder(order);
        ReflectionTestUtils.setField(other, "id", 999L);
        given(orderItemRepository.findAllByIdInForUpdate(List.of(ORDER_ITEM_ID)))
                .willReturn(List.of(orderItem(order, other, ORDER_ITEM_ID, 3)));

        assertThatThrownBy(() -> createDefault())
                .isInstanceOf(OrderException.class).hasMessageContaining("같은 판매자 주문");
    }

    @ParameterizedTest
    @EnumSource(value = SellerOrderStatus.class, names = {
            "PENDING_PAYMENT", "PAID", "PREPARING", "SHIPPED", "CANCELLED"
    })
    void rejectsNonDeliveredSellerOrder(SellerOrderStatus status) {
        ReflectionTestUtils.setField(sellerOrder, "status", status);
        assertThatThrownBy(() -> createDefault()).isInstanceOf(OrderException.class);
    }

    @Test
    void subtractsCanceledReturnedAndAllActiveReturnQuantities() {
        ReflectionTestUtils.setField(orderItem, "canceledQuantity", 1);
        ReflectionTestUtils.setField(orderItem, "returnedQuantity", 1);
        PendingReturnQuantityProjection projection = mock(PendingReturnQuantityProjection.class);
        given(projection.getOrderItemId()).willReturn(ORDER_ITEM_ID);
        given(projection.getPendingQuantity()).willReturn(1L);
        given(returnRequestRepository.sumItemQuantitiesByStatuses(any(), any())).willReturn(List.of(projection));

        assertThatThrownBy(() -> createDefault()).isInstanceOf(OrderException.class);

        @SuppressWarnings("unchecked")
        var statuses = org.mockito.ArgumentCaptor.forClass(Set.class);
        verify(returnRequestRepository).sumItemQuantitiesByStatuses(any(), statuses.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                ReturnRequestStatus.REQUESTED, ReturnRequestStatus.APPROVED,
                ReturnRequestStatus.COLLECTING, ReturnRequestStatus.RECEIVED,
                ReturnRequestStatus.INSPECTED, ReturnRequestStatus.REFUNDING
        ).doesNotContain(ReturnRequestStatus.REJECTED, ReturnRequestStatus.CANCELED,
                ReturnRequestStatus.FAILED, ReturnRequestStatus.COMPLETED);
    }

    @Test
    void rejectsDuplicateItemBeforeLocks() {
        ReturnRequestItemRequest duplicate = item(ORDER_ITEM_ID, 1);
        assertThatThrownBy(() -> service.create(
                USER_ID, ORDER_ID, SELLER_ORDER_ID,
                request(ReturnReasonType.CHANGE_OF_MIND, duplicate, duplicate)
        )).isInstanceOf(OrderException.class).hasMessageContaining("중복");
        verify(orderRepository, never()).findByIdAndUserIdForUpdate(anyLong(), anyLong());
    }

    @Test
    void allowsBuyerReturnAtExactlySevenDaysAndRejectsAfterBoundary() {
        shipment = deliveredShipment(sellerOrder, NOW.minusDays(7));
        given(shipmentRepository.findBySellerOrderIdAndType(anyLong(), any())).willReturn(Optional.of(shipment));
        assertThat(service.create(USER_ID, ORDER_ID, SELLER_ORDER_ID,
                request(ReturnReasonType.CHANGE_OF_MIND, item(ORDER_ITEM_ID, 1)))).isNotNull();

        shipment = deliveredShipment(sellerOrder, NOW.minusDays(7).minusNanos(1));
        given(shipmentRepository.findBySellerOrderIdAndType(anyLong(), any())).willReturn(Optional.of(shipment));
        assertThatThrownBy(() -> service.create(USER_ID, ORDER_ID, SELLER_ORDER_ID,
                request(ReturnReasonType.CHANGE_OF_MIND, item(ORDER_ITEM_ID, 1))))
                .isInstanceOf(OrderException.class).hasMessageContaining("기간");
    }

    @Test
    void rejectsMissingOutboundOrDeliveredAt() {
        given(shipmentRepository.findBySellerOrderIdAndType(anyLong(), any())).willReturn(Optional.empty());
        assertThatThrownBy(() -> createDefault()).isInstanceOf(OrderException.class);

        shipment = Shipment.createShipped(sellerOrder, ShipmentType.ORIGINAL_OUTBOUND, "택배", "123", NOW.minusDays(2));
        given(shipmentRepository.findBySellerOrderIdAndType(anyLong(), any())).willReturn(Optional.of(shipment));
        assertThatThrownBy(() -> createDefault()).isInstanceOf(OrderException.class);
    }

    @Test
    void identicalKeyReturnsExistingAndDifferentPayloadIsRejected() {
        ReturnRequestCreateRequest request = request(ReturnReasonType.CHANGE_OF_MIND, item(ORDER_ITEM_ID, 1));
        ReturnRequest existing = existingRequest(request);
        ReturnRequestItem existingItem = ReturnRequestItem.create(existing, orderItem, 1);
        given(returnRequestRepository.findByClientRequestKey(request.clientRequestKey()))
                .willReturn(Optional.of(existing));
        given(returnRequestItemRepository.findAllByReturnRequestIdOrderByIdAsc(100L))
                .willReturn(List.of(existingItem));

        assertThat(service.create(USER_ID, ORDER_ID, SELLER_ORDER_ID, request).returnRequestId()).isEqualTo(100L);
        verify(orderRepository, never()).findByIdAndUserIdForUpdate(anyLong(), anyLong());

        ReturnRequestCreateRequest changed = new ReturnRequestCreateRequest(
                request.clientRequestKey(), request.reasonType(), "다른 사유",
                request.collectionRecipientName(), request.collectionPhone(),
                request.collectionPostalCode(), request.collectionAddress(),
                request.collectionAddressDetail(), request.items()
        );
        assertThatThrownBy(() -> service.create(USER_ID, ORDER_ID, SELLER_ORDER_ID, changed))
                .isInstanceOf(OrderException.class).hasMessageContaining("다른 내용");
    }

    @Test
    void convertsUniqueRaceToSafeConflict() {
        given(returnRequestRepository.saveAndFlush(any(ReturnRequest.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));
        assertThatThrownBy(() -> createDefault())
                .isInstanceOf(OrderException.class).hasMessageContaining("이미 사용된");
        verify(returnRequestItemRepository, never()).saveAll(anyList());
    }

    @Test
    void locksOrderSellerOrderAndSortedItemsBeforeFinalQuantityCheck() {
        createDefault();
        InOrder order = inOrder(orderRepository, sellerOrderRepository, orderItemRepository, returnRequestRepository);
        order.verify(orderRepository).findByIdAndUserIdForUpdate(ORDER_ID, USER_ID);
        order.verify(sellerOrderRepository).findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID);
        order.verify(orderItemRepository).findAllByIdInForUpdate(List.of(ORDER_ITEM_ID));
        order.verify(returnRequestRepository).sumItemQuantitiesByStatuses(any(), any());
    }

    private ReturnRequestResponse createDefault() {
        return service.create(USER_ID, ORDER_ID, SELLER_ORDER_ID,
                request(ReturnReasonType.CHANGE_OF_MIND, item(ORDER_ITEM_ID, 2)));
    }

    private ReturnRequest existingRequest(ReturnRequestCreateRequest request) {
        ReturnRequest value = ReturnRequest.createRequested(
                order, sellerOrder, request.clientRequestKey(), request.reasonType(), request.reason().trim(),
                request.collectionRecipientName().trim(), request.collectionPhone().trim(),
                request.collectionPostalCode().trim(), request.collectionAddress().trim(),
                request.collectionAddressDetail().trim(), NOW
        );
        ReflectionTestUtils.setField(value, "id", 100L);
        return value;
    }

    private ReturnRequestCreateRequest request(ReturnReasonType reason, ReturnRequestItemRequest... items) {
        return new ReturnRequestCreateRequest(
                UUID.randomUUID().toString(), reason, " 반품 사유 ", " 구매자 ",
                " 010-1234-5678 ", " 12345 ", " 서울시 강남구 ", " 101호 ", List.of(items)
        );
    }

    private ReturnRequestItemRequest item(long id, int quantity) {
        return new ReturnRequestItemRequest(id, quantity);
    }

    private Order paidOrder(User owner) {
        Order value = Order.createPendingPayment(
                "GM-ORDER", owner, 30_000L, 0L,
                "수령인", "010-1234-5678", "12345", "서울", null
        );
        ReflectionTestUtils.setField(value, "id", ORDER_ID);
        value.markPaid(NOW.minusDays(10));
        return value;
    }

    private SellerOrder deliveredSellerOrder(Order parent) {
        SellerOrder value = SellerOrder.createPendingPayment(parent, mock(Seller.class));
        ReflectionTestUtils.setField(value, "id", SELLER_ORDER_ID);
        value.markPaid();
        value.prepare(NOW.minusDays(3));
        value.markShipped(NOW.minusDays(2));
        value.markDelivered(NOW.minusDays(1));
        return value;
    }

    private Shipment deliveredShipment(SellerOrder parent, LocalDateTime deliveredAt) {
        Shipment value = Shipment.createShipped(
                parent, ShipmentType.ORIGINAL_OUTBOUND, "택배", UUID.randomUUID().toString(),
                deliveredAt.minusDays(1)
        );
        value.deliver(deliveredAt);
        return value;
    }

    private OrderItem orderItem(Order parent, SellerOrder sellerOrder, long id, int quantity) {
        OrderItem value = OrderItem.create(
                parent, mock(Product.class), null, mock(Seller.class), sellerOrder, null,
                "상품", null, "상점", null, "색상: 빨강", 10_000L, 0L,
                quantity, true, 0L, 3_000L, 6_000L
        );
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }
}
