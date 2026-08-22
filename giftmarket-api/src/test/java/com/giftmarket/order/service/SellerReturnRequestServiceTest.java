package com.giftmarket.order.service;

import com.giftmarket.order.dto.request.SellerReturnInspectRequest;
import com.giftmarket.order.dto.request.SellerReturnInspectionItemRequest;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.order.dto.response.SellerReturnRequestPageResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerReturnRequestServiceTest {

    private static final long USER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long ORDER_ID = 3L;
    private static final long SELLER_ORDER_ID = 4L;
    private static final long RETURN_ID = 5L;
    private static final long ITEM_ID = 6L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 21, 15, 0);

    @Mock SellerRepository sellerRepository;
    @Mock OrderRepository orderRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock ReturnRequestItemRepository returnRequestItemRepository;
    @Mock ShipmentRepository shipmentRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock ReturnRefundCalculationService returnRefundCalculationService;

    private SellerReturnRequestService service;
    private Seller seller;
    private Order order;
    private SellerOrder sellerOrder;
    private OrderItem orderItem;
    private ReturnRequest request;
    private ReturnRequestItem requestItem;
    private ReturnRequestOwnershipProjection ownership;

    @BeforeEach
    void setUp() {
        service = spy(new SellerReturnRequestService(
                sellerRepository, orderRepository, sellerOrderRepository, orderItemRepository,
                returnRequestRepository, returnRequestItemRepository, shipmentRepository,
                paymentRepository, returnRefundCalculationService
        ));
        doReturn(NOW).when(service).currentTime();
        seller = mock(Seller.class);
        given(seller.getId()).willReturn(SELLER_ID);
        given(seller.getStatus()).willReturn(SellerStatus.ACTIVE);
        given(sellerRepository.findByUserId(USER_ID)).willReturn(Optional.of(seller));
        order = paidOrder();
        sellerOrder = deliveredSellerOrder();
        orderItem = orderItem(ITEM_ID);
        request = requested(ReturnReasonType.CHANGE_OF_MIND);
        requestItem = ReturnRequestItem.create(request, orderItem, 1);
        ownership = mock(ReturnRequestOwnershipProjection.class);
        given(ownership.getOrderId()).willReturn(ORDER_ID);
        given(ownership.getSellerOrderId()).willReturn(SELLER_ORDER_ID);
        given(returnRequestRepository.findOwnership(RETURN_ID, SELLER_ID)).willReturn(Optional.of(ownership));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(sellerOrderRepository.findByIdAndSellerIdForUpdate(SELLER_ORDER_ID, SELLER_ID))
                .willReturn(Optional.of(sellerOrder));
        given(returnRequestRepository.findByIdForUpdate(RETURN_ID)).willReturn(Optional.of(request));
        given(returnRequestRepository.findById(RETURN_ID)).willReturn(Optional.of(request));
        given(returnRequestItemRepository.findAllByReturnRequestIdOrderByIdAsc(RETURN_ID))
                .willReturn(List.of(requestItem));
        given(orderItemRepository.findAllByIdInForUpdate(List.of(ITEM_ID))).willReturn(List.of(orderItem));
        given(shipmentRepository.save(any(Shipment.class))).willAnswer(invocation -> {
            Shipment shipment = invocation.getArgument(0);
            ReflectionTestUtils.setField(shipment, "id", 50L);
            return shipment;
        });
        Payment payment = mock(Payment.class);
        given(payment.getId()).willReturn(60L);
        given(paymentRepository.findFirstByOrderIdOrderByIdDesc(ORDER_ID))
                .willReturn(Optional.of(payment));
        given(paymentRepository.findByIdForUpdate(60L)).willReturn(Optional.of(payment));
        given(orderItemRepository.findAllBySellerOrderIdForUpdate(SELLER_ORDER_ID))
                .willReturn(List.of(orderItem));
    }

    @Test
    void listsSellerReturnsWithStatusFilterAndBatchItems() {
        given(returnRequestRepository.findSellerReturns(
                SELLER_ID, ReturnRequestStatus.REQUESTED, PageRequest.of(0, 20)
        )).willReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));
        given(returnRequestItemRepository
                .findAllByReturnRequestIdInOrderByReturnRequestIdAscOrderItemIdAsc(List.of(RETURN_ID)))
                .willReturn(List.of(requestItem));

        SellerReturnRequestPageResponse response = service.getReturns(
                USER_ID, ReturnRequestStatus.REQUESTED, 0, 20
        );

        assertThat(response.returns()).singleElement().satisfies(value -> {
            assertThat(value.returnRequestId()).isEqualTo(RETURN_ID);
            assertThat(value.orderId()).isEqualTo(ORDER_ID);
            assertThat(value.items()).singleElement()
                    .extracting(item -> item.productName()).isEqualTo("상품 snapshot");
        });
    }

    @Test
    void getsOwnedReturnAndBlocksAnotherSeller() {
        assertThat(service.getReturn(USER_ID, RETURN_ID).returnRequestId()).isEqualTo(RETURN_ID);
        given(returnRequestRepository.findOwnership(RETURN_ID, SELLER_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getReturn(USER_ID, RETURN_ID))
                .isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.approve(USER_ID, RETURN_ID, null))
                .isInstanceOf(SellerException.class);
    }

    @Test
    void approvesNormalReasonWithoutChangingResponsibility() {
        ReturnRequestResponse response = service.approve(USER_ID, RETURN_ID, null);
        assertThat(response.status()).isEqualTo(ReturnRequestStatus.APPROVED);
        assertThat(response.responsibility()).isEqualTo(ReturnResponsibility.BUYER);
        assertThat(orderItem.getReturnedQuantity()).isZero();
    }

    @Test
    void defectiveKeepsSellerResponsibilityAndNormalReasonCannotBeChanged() {
        resetRequest(ReturnReasonType.DEFECTIVE);
        assertThat(service.approve(USER_ID, RETURN_ID, null).responsibility())
                .isEqualTo(ReturnResponsibility.SELLER);
        resetRequest(ReturnReasonType.CHANGE_OF_MIND);
        assertThatThrownBy(() -> service.approve(USER_ID, RETURN_ID, ReturnResponsibility.SELLER))
                .isInstanceOf(SellerException.class);
    }

    @Test
    void otherRequiresAndConfirmsResponsibility() {
        resetRequest(ReturnReasonType.OTHER);
        assertThatThrownBy(() -> service.approve(USER_ID, RETURN_ID, null))
                .isInstanceOf(SellerException.class);
        resetRequest(ReturnReasonType.OTHER);
        assertThat(service.approve(USER_ID, RETURN_ID, ReturnResponsibility.BUYER).responsibility())
                .isEqualTo(ReturnResponsibility.BUYER);
        resetRequest(ReturnReasonType.OTHER);
        assertThat(service.approve(USER_ID, RETURN_ID, ReturnResponsibility.SELLER).responsibility())
                .isEqualTo(ReturnResponsibility.SELLER);
    }

    @Test
    void reapprovalAndApproveRejectRaceAreBlockedByLockedState() {
        service.approve(USER_ID, RETURN_ID, null);
        assertThatThrownBy(() -> service.approve(USER_ID, RETURN_ID, null))
                .isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.reject(USER_ID, RETURN_ID, "거절"))
                .isInstanceOf(SellerException.class);
    }

    @Test
    void rejectsRequestedReturnWithTrimmedReasonWithoutShipment() {
        ReturnRequestResponse response = service.reject(USER_ID, RETURN_ID, "  회수 불가  ");
        assertThat(response.status()).isEqualTo(ReturnRequestStatus.REJECTED);
        assertThat(response.rejectedReason()).isEqualTo("회수 불가");
        assertThat(response.collectionShipment()).isNull();
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void startsCollectionWithSeparateShippedReturnShipment() {
        request.approve(NOW.minusMinutes(1));
        Shipment original = Shipment.createShipped(
                sellerOrder, ShipmentType.ORIGINAL_OUTBOUND, "기존택배", "OUT-1", NOW.minusDays(3)
        );

        ReturnRequestResponse response = service.collect(
                USER_ID, RETURN_ID, "  회수택배  ", "  RET-1  "
        );

        assertThat(response.status()).isEqualTo(ReturnRequestStatus.COLLECTING);
        assertThat(response.collectionShipment()).satisfies(value -> {
            assertThat(value.type()).isEqualTo(ShipmentType.RETURN_COLLECTION);
            assertThat(value.status()).isEqualTo(ShipmentStatus.SHIPPED);
            assertThat(value.shippingCompany()).isEqualTo("회수택배");
        });
        assertThat(original.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(original.getTrackingNumber()).isEqualTo("OUT-1");
    }

    @Test
    void duplicateCollectionStartIsBlocked() {
        request.approve(NOW.minusMinutes(1));
        service.collect(USER_ID, RETURN_ID, "택배", "RET-1");
        assertThatThrownBy(() -> service.collect(USER_ID, RETURN_ID, "택배", "RET-2"))
                .isInstanceOf(SellerException.class);
        verify(shipmentRepository, times(1)).save(any());
    }

    @Test
    void receivesCollectionAndDeliversShipmentOnce() {
        startCollection();
        ReturnRequestResponse response = service.receive(USER_ID, RETURN_ID);
        assertThat(response.status()).isEqualTo(ReturnRequestStatus.RECEIVED);
        assertThat(response.collectionShipment().status()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(response.collectionShipment().deliveredAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.receive(USER_ID, RETURN_ID))
                .isInstanceOf(SellerException.class);
    }

    @Test
    void receiveFromWrongStateIsBlocked() {
        assertThatThrownBy(() -> service.receive(USER_ID, RETURN_ID))
                .isInstanceOf(SellerException.class);
    }

    @Test
    void inspectsEveryItemWithMixedResultsAndDoesNotChangeReturnQuantity() {
        OrderItem secondOrderItem = orderItem(7L);
        ReturnRequestItem second = ReturnRequestItem.create(request, secondOrderItem, 1);
        receiveCollection();
        given(returnRequestItemRepository.findAllByReturnRequestIdOrderByIdAsc(RETURN_ID))
                .willReturn(List.of(requestItem, second));
        given(orderItemRepository.findAllBySellerOrderIdForUpdate(SELLER_ORDER_ID))
                .willReturn(List.of(orderItem, secondOrderItem));

        ReturnRequestResponse response = service.inspect(USER_ID, RETURN_ID,
                inspect(item(ITEM_ID, ReturnInspectionResult.RESTOCKABLE),
                        item(7L, ReturnInspectionResult.NON_RESTOCKABLE)));

        assertThat(response.status()).isEqualTo(ReturnRequestStatus.INSPECTED);
        assertThat(requestItem.getInspectionResult()).isEqualTo(ReturnInspectionResult.RESTOCKABLE);
        assertThat(second.getInspectionResult()).isEqualTo(ReturnInspectionResult.NON_RESTOCKABLE);
        assertThat(orderItem.getReturnedQuantity()).isZero();
        assertThat(secondOrderItem.getReturnedQuantity()).isZero();
        verify(returnRefundCalculationService).confirm(
                any(Payment.class), eq(sellerOrder), eq(request),
                eq(List.of(orderItem, secondOrderItem)), eq(List.of(requestItem, second))
        );
    }

    @Test
    void inspectionRequiresExactUniqueRequestItemsAndCannotRepeat() {
        receiveCollection();
        assertThatThrownBy(() -> service.inspect(USER_ID, RETURN_ID,
                inspect(item(ITEM_ID, ReturnInspectionResult.RESTOCKABLE),
                        item(ITEM_ID, ReturnInspectionResult.NON_RESTOCKABLE))))
                .isInstanceOf(SellerException.class).hasMessageContaining("중복");
        assertThatThrownBy(() -> service.inspect(USER_ID, RETURN_ID,
                inspect(item(999L, ReturnInspectionResult.RESTOCKABLE))))
                .isInstanceOf(SellerException.class).hasMessageContaining("모든 상품");

        service.inspect(USER_ID, RETURN_ID,
                inspect(item(ITEM_ID, ReturnInspectionResult.RESTOCKABLE)));
        assertThatThrownBy(() -> service.inspect(USER_ID, RETURN_ID,
                inspect(item(ITEM_ID, ReturnInspectionResult.RESTOCKABLE))))
                .isInstanceOf(SellerException.class);
    }

    @Test
    void locksOrderSellerOrderReturnAndSortedItemsInOrder() {
        service.approve(USER_ID, RETURN_ID, null);
        InOrder locks = inOrder(orderRepository, sellerOrderRepository,
                returnRequestRepository, orderItemRepository);
        locks.verify(orderRepository).findByIdForUpdate(ORDER_ID);
        locks.verify(sellerOrderRepository).findByIdAndSellerIdForUpdate(SELLER_ORDER_ID, SELLER_ID);
        locks.verify(returnRequestRepository).findByIdForUpdate(RETURN_ID);
        locks.verify(orderItemRepository).findAllByIdInForUpdate(List.of(ITEM_ID));
    }

    @Test
    void inspectionLocksPaymentBeforeOrderAndSellerOrder() {
        receiveCollection();

        service.inspect(USER_ID, RETURN_ID,
                inspect(item(ITEM_ID, ReturnInspectionResult.RESTOCKABLE)));

        InOrder locks = inOrder(paymentRepository, orderRepository, sellerOrderRepository,
                returnRequestRepository, orderItemRepository);
        locks.verify(paymentRepository).findByIdForUpdate(60L);
        locks.verify(orderRepository).findByIdForUpdate(ORDER_ID);
        locks.verify(sellerOrderRepository).findByIdAndSellerIdForUpdate(SELLER_ORDER_ID, SELLER_ID);
        locks.verify(returnRequestRepository).findByIdForUpdate(RETURN_ID);
        locks.verify(orderItemRepository).findAllBySellerOrderIdForUpdate(SELLER_ORDER_ID);
    }

    private void startCollection() {
        request.approve(NOW.minusMinutes(2));
        service.collect(USER_ID, RETURN_ID, "택배", "RET-1");
    }

    private void receiveCollection() {
        startCollection();
        service.receive(USER_ID, RETURN_ID);
    }

    private void resetRequest(ReturnReasonType type) {
        request = requested(type);
        requestItem = ReturnRequestItem.create(request, orderItem, 1);
        given(returnRequestRepository.findByIdForUpdate(RETURN_ID)).willReturn(Optional.of(request));
        given(returnRequestRepository.findById(RETURN_ID)).willReturn(Optional.of(request));
        given(returnRequestItemRepository.findAllByReturnRequestIdOrderByIdAsc(RETURN_ID))
                .willReturn(List.of(requestItem));
    }

    private SellerReturnInspectRequest inspect(SellerReturnInspectionItemRequest... items) {
        return new SellerReturnInspectRequest(List.of(items));
    }

    private SellerReturnInspectionItemRequest item(long id, ReturnInspectionResult result) {
        return new SellerReturnInspectionItemRequest(id, result);
    }

    private ReturnRequest requested(ReturnReasonType type) {
        ReturnRequest value = ReturnRequest.createRequested(
                order, sellerOrder, UUID.randomUUID().toString(), type, "사유",
                "구매자", "010-1234-5678", "12345", "서울", "101호", NOW.minusHours(1)
        );
        ReflectionTestUtils.setField(value, "id", RETURN_ID);
        return value;
    }

    private Order paidOrder() {
        Order value = Order.createPendingPayment(
                "GM-ORDER", mock(User.class), 20_000L, 0L,
                "구매자", "010-1234-5678", "12345", "서울", null
        );
        ReflectionTestUtils.setField(value, "id", ORDER_ID);
        value.markPaid(NOW.minusDays(5));
        return value;
    }

    private SellerOrder deliveredSellerOrder() {
        SellerOrder value = SellerOrder.createPendingPayment(order, seller);
        ReflectionTestUtils.setField(value, "id", SELLER_ORDER_ID);
        value.markPaid();
        value.prepare(NOW.minusDays(4));
        value.markShipped(NOW.minusDays(3));
        value.markDelivered(NOW.minusDays(2));
        return value;
    }

    private OrderItem orderItem(long id) {
        OrderItem value = OrderItem.create(
                order, mock(Product.class), null, seller, sellerOrder, null,
                "상품 snapshot", null, "상점", null, "색상: 블랙",
                20_000L, 0L, 2, true, 0L, 3_000L, 6_000L
        );
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }
}
