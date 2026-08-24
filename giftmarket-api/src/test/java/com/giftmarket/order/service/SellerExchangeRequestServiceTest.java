package com.giftmarket.order.service;

import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.order.dto.response.ExchangeRequestResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerExchangeRequestServiceTest {
    private static final long USER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long ORDER_ID = 10L;
    private static final long SELLER_ORDER_ID = 20L;
    private static final long EXCHANGE_ID = 30L;
    private static final long ORDER_ITEM_ID = 40L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 12, 0);

    @Mock SellerRepository sellerRepository;
    @Mock OrderRepository orderRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ExchangeRequestRepository exchangeRequestRepository;
    @Mock ExchangeRequestItemRepository exchangeRequestItemRepository;
    @Mock ExchangeRequestImageRepository exchangeRequestImageRepository;
    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock OrderInventoryService orderInventoryService;
    @Mock StorageService storageService;

    private SellerExchangeRequestService service;
    private Seller seller;
    private Order order;
    private SellerOrder sellerOrder;
    private OrderItem orderItem;
    private ExchangeRequest request;
    private ExchangeRequestItem requestItem;

    @BeforeEach
    void setUp() {
        service = spy(new SellerExchangeRequestService(
                sellerRepository, orderRepository, sellerOrderRepository, orderItemRepository,
                exchangeRequestRepository, exchangeRequestItemRepository, exchangeRequestImageRepository,
                returnRequestRepository, orderInventoryService, storageService
        ));
        doReturn(NOW).when(service).currentTime();
        seller = mock(Seller.class);
        given(seller.getId()).willReturn(SELLER_ID);
        given(seller.getStatus()).willReturn(SellerStatus.ACTIVE);
        User user = mock(User.class);
        given(user.getId()).willReturn(99L);
        order = Order.createPendingPayment("GM-ORDER", user, 20_000L, 0L,
                "구매자", "010-1234-5678", "12345", "서울", null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        sellerOrder = SellerOrder.createPendingPayment(order, seller);
        ReflectionTestUtils.setField(sellerOrder, "id", SELLER_ORDER_ID);
        Product product = mock(Product.class);
        given(product.getId()).willReturn(50L);
        orderItem = OrderItem.create(order, product, null, seller, sellerOrder, null,
                "상품", null, "상점", null, null, 10_000L, 0L,
                2, true, 0L, 3_000L, 6_000L);
        ReflectionTestUtils.setField(orderItem, "id", ORDER_ITEM_ID);
        setRequest(ExchangeReasonType.CHANGE_OF_MIND);

        given(sellerRepository.findByUserId(USER_ID)).willReturn(Optional.of(seller));
        ExchangeRequestOwnershipProjection ownership = mock(ExchangeRequestOwnershipProjection.class);
        given(ownership.getOrderId()).willReturn(ORDER_ID);
        given(ownership.getSellerOrderId()).willReturn(SELLER_ORDER_ID);
        given(exchangeRequestRepository.findOwnership(EXCHANGE_ID, SELLER_ID)).willReturn(Optional.of(ownership));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(sellerOrderRepository.findByIdAndSellerIdForUpdate(SELLER_ORDER_ID, SELLER_ID))
                .willReturn(Optional.of(sellerOrder));
        given(exchangeRequestRepository.findByIdForUpdate(EXCHANGE_ID)).willAnswer(invocation -> Optional.of(request));
        given(exchangeRequestRepository.findById(EXCHANGE_ID)).willAnswer(invocation -> Optional.of(request));
        given(exchangeRequestItemRepository.findAllByExchangeRequestIdOrderByOrderItemIdAsc(EXCHANGE_ID))
                .willAnswer(invocation -> List.of(requestItem));
        given(orderItemRepository.findAllByIdInForUpdate(List.of(ORDER_ITEM_ID))).willReturn(List.of(orderItem));
        given(returnRequestRepository.sumItemQuantitiesByStatuses(any(), any())).willReturn(List.of());
        given(exchangeRequestRepository.sumItemQuantitiesByStatusesExcludingRequest(any(), eq(EXCHANGE_ID), any()))
                .willReturn(List.of());
        doAnswer(invocation -> {
            List<ExchangeRequestItem> items = invocation.getArgument(0);
            items.forEach(item -> item.reserveTargetStock(item.getQuantity()));
            return null;
        }).when(orderInventoryService).reserveExchangeTargets(anyList());
    }

    @Test
    void listsWithStatusFilterAndGetsOwnedDetail() {
        given(exchangeRequestRepository.findSellerExchanges(eq(SELLER_ID),
                eq(ExchangeRequestStatus.REQUESTED), any()))
                .willReturn(new PageImpl<>(List.of(request)));
        given(exchangeRequestItemRepository
                .findAllByExchangeRequestIdInOrderByExchangeRequestIdAscOrderItemIdAsc(List.of(EXCHANGE_ID)))
                .willReturn(List.of(requestItem));
        given(exchangeRequestImageRepository
                .findAllByExchangeRequestIdInOrderByExchangeRequestIdAscSortOrderAsc(List.of(EXCHANGE_ID)))
                .willReturn(List.of());

        assertThat(service.getExchanges(USER_ID, ExchangeRequestStatus.REQUESTED, 0, 20).exchanges())
                .singleElement().extracting(ExchangeRequestResponse::exchangeRequestId).isEqualTo(EXCHANGE_ID);
        assertThat(service.getExchange(USER_ID, EXCHANGE_ID).items()).hasSize(1);
    }

    @Test
    void hidesAnotherSellersExchange() {
        given(exchangeRequestRepository.findOwnership(EXCHANGE_ID, SELLER_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getExchange(USER_ID, EXCHANGE_ID))
                .isInstanceOf(SellerException.class).hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    void buyerApprovalReservesAndMovesToPaymentPendingForExactly24Hours() {
        ExchangeRequestResponse response = service.approve(USER_ID, EXCHANGE_ID, null);

        assertThat(response.status()).isEqualTo(ExchangeRequestStatus.PAYMENT_PENDING);
        assertThat(response.approvedAt()).isEqualTo(NOW);
        assertThat(response.paymentPendingAt()).isEqualTo(NOW);
        assertThat(response.paymentDueAt()).isEqualTo(NOW.plusHours(24));
        assertThat(requestItem.getReservedQuantity()).isEqualTo(requestItem.getQuantity());
        assertThat(requestItem.getReleasedQuantity()).isZero();
        assertThat(requestItem.getConsumedQuantity()).isZero();
        verify(orderInventoryService).reserveExchangeTargets(List.of(requestItem));
    }

    @Test
    void sellerApprovalReservesAndMovesToCollectingWithoutShipment() {
        setRequest(ExchangeReasonType.DEFECTIVE);

        ExchangeRequestResponse response = service.approve(USER_ID, EXCHANGE_ID, null);

        assertThat(response.status()).isEqualTo(ExchangeRequestStatus.COLLECTING);
        assertThat(response.collectionShipment()).isNull();
        assertThat(requestItem.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    void otherRequiresResponsibilityAndFollowsSelectedFlow() {
        setRequest(ExchangeReasonType.OTHER);
        assertThatThrownBy(() -> service.approve(USER_ID, EXCHANGE_ID, null))
                .isInstanceOf(SellerException.class).hasMessageContaining("귀책 주체");

        setRequest(ExchangeReasonType.OTHER);
        assertThat(service.approve(USER_ID, EXCHANGE_ID, ExchangeResponsibility.BUYER).status())
                .isEqualTo(ExchangeRequestStatus.PAYMENT_PENDING);

        setRequest(ExchangeReasonType.OTHER);
        assertThat(service.approve(USER_ID, EXCHANGE_ID, ExchangeResponsibility.SELLER).status())
                .isEqualTo(ExchangeRequestStatus.COLLECTING);
    }

    @Test
    void normalReasonResponsibilityCannotBeOverridden() {
        assertThatThrownBy(() -> service.approve(USER_ID, EXCHANGE_ID, ExchangeResponsibility.SELLER))
                .isInstanceOf(SellerException.class).hasMessageContaining("변경할 수 없습니다");
        verify(orderInventoryService, never()).reserveExchangeTargets(anyList());
    }

    @Test
    void quantityConflictKeepsRequestedAndDoesNotReserve() {
        PendingReturnQuantityProjection pending = mock(PendingReturnQuantityProjection.class);
        given(pending.getOrderItemId()).willReturn(ORDER_ITEM_ID);
        given(pending.getPendingQuantity()).willReturn(2L);
        given(returnRequestRepository.sumItemQuantitiesByStatuses(any(), any())).willReturn(List.of(pending));

        assertThatThrownBy(() -> service.approve(USER_ID, EXCHANGE_ID, null))
                .isInstanceOf(SellerException.class).hasMessageContaining("수량이 부족");
        assertThat(request.getStatus()).isEqualTo(ExchangeRequestStatus.REQUESTED);
        assertThat(requestItem.getReservedQuantity()).isZero();
        verify(orderInventoryService, never()).reserveExchangeTargets(anyList());
    }

    @Test
    void inventoryFailureKeepsRequestAndReservationUnchanged() {
        doThrow(new OrderException("교환 대상 재고가 부족합니다."))
                .when(orderInventoryService).reserveExchangeTargets(anyList());

        assertThatThrownBy(() -> service.approve(USER_ID, EXCHANGE_ID, null))
                .isInstanceOf(OrderException.class).hasMessageContaining("재고가 부족");
        assertThat(request.getStatus()).isEqualTo(ExchangeRequestStatus.REQUESTED);
        assertThat(request.getApprovedAt()).isNull();
        assertThat(requestItem.getReservedQuantity()).isZero();
    }

    @Test
    void duplicateApprovalAndRejectAfterApprovalAreBlockedWithoutSecondReservation() {
        service.approve(USER_ID, EXCHANGE_ID, null);

        assertThatThrownBy(() -> service.approve(USER_ID, EXCHANGE_ID, null))
                .isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.reject(USER_ID, EXCHANGE_ID, "거절"))
                .isInstanceOf(SellerException.class);
        verify(orderInventoryService, times(1)).reserveExchangeTargets(anyList());
    }

    @Test
    void rejectsRequestedExchangeWithoutInventoryMutation() {
        ExchangeRequestResponse response = service.reject(USER_ID, EXCHANGE_ID, " 재고 확보 불가 ");
        assertThat(response.status()).isEqualTo(ExchangeRequestStatus.REJECTED);
        assertThat(response.rejectedReason()).isEqualTo("재고 확보 불가");
        verify(orderInventoryService, never()).reserveExchangeTargets(anyList());
    }

    private void setRequest(ExchangeReasonType reasonType) {
        request = ExchangeRequest.createRequested(
                order, sellerOrder, "exchange-key", reasonType, "교환 사유",
                "회수인", "010-1111-2222", "12345", "회수 주소", null,
                "수령인", "010-3333-4444", "54321", "재배송 주소", "101호", NOW.minusDays(1)
        );
        ReflectionTestUtils.setField(request, "id", EXCHANGE_ID);
        requestItem = ExchangeRequestItem.create(
                request, orderItem, 1, orderItem.getProduct(), null,
                "상품", null, 10_000L
        );
    }
}
