package com.giftmarket.order.service;

import com.giftmarket.order.dto.request.SellerOrderShipRequest;
import com.giftmarket.order.dto.response.SellerOrderDetailResponse;
import com.giftmarket.order.dto.response.SellerOrderPageResponse;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderItemSummaryProjection;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SellerOrderManagementServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SELLER_ID = 10L;
    private static final Long ORDER_ID = 20L;
    private static final Long SELLER_ORDER_ID = 30L;

    @Mock SellerRepository sellerRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock OrderCancellationRepository orderCancellationRepository;
    @Mock Seller seller;
    @Mock User user;

    private SellerOrderManagementService service;
    private Order order;
    private SellerOrder sellerOrder;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        service = new SellerOrderManagementService(
                sellerRepository,
                sellerOrderRepository,
                orderRepository,
                orderItemRepository,
                orderCancellationRepository
        );
        lenient().when(sellerRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(seller));
        lenient().when(seller.getId()).thenReturn(SELLER_ID);
        lenient().when(seller.getStatus()).thenReturn(SellerStatus.ACTIVE);

        order = Order.createPendingPayment(
                "GM-ORDER-100", user, 10_000L, 0L,
                "수령인", "010-1234-5678", "12345", "서울시 테스트로", "101호"
        );
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.markPaid(java.time.LocalDateTime.now());
        sellerOrder = SellerOrder.createPendingPayment(order, seller);
        sellerOrder.markPaid();
        ReflectionTestUtils.setField(sellerOrder, "id", SELLER_ORDER_ID);
        orderItem = orderItem();
    }

    @Test
    void listsOnlyCurrentSellerPaidOrdersWithFilterKeywordAndPagination() {
        SellerOrderItemSummaryProjection summary = summary();
        given(sellerOrderRepository.findSellerOrders(
                eq(SELLER_ID),
                eq(SellerOrderStatus.PENDING_PAYMENT),
                eq(SellerOrderStatus.PAID),
                eq("상품"),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(sellerOrder)));
        given(orderItemRepository.summarizeBySellerOrderIds(
                List.of(SELLER_ORDER_ID)
        )).willReturn(List.of(summary));

        SellerOrderPageResponse response = service.getSellerOrders(
                USER_ID, SellerOrderStatus.PAID, " 상품 ", 0, 20
        );

        assertThat(response.orders()).hasSize(1);
        assertThat(response.orders().getFirst().sellerOrderId())
                .isEqualTo(SELLER_ORDER_ID);
        assertThat(response.orders().getFirst().recipientName()).isEqualTo("수령인");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sellerOrderRepository).findSellerOrders(
                eq(SELLER_ID), eq(SellerOrderStatus.PENDING_PAYMENT),
                eq(SellerOrderStatus.PAID), eq("상품"), pageable.capture()
        );
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void pendingPaymentIsNeverAcceptedAsSellerListFilter() {
        assertThatThrownBy(() -> service.getSellerOrders(
                USER_ID, SellerOrderStatus.PENDING_PAYMENT, null, 0, 20
        )).isInstanceOf(SellerException.class);
        verify(sellerOrderRepository, never()).findSellerOrders(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void detailContainsOnlyItemsLoadedBySellerOrderId() {
        given(sellerOrderRepository.findByIdAndSellerId(
                SELLER_ORDER_ID, SELLER_ID
        )).willReturn(Optional.of(sellerOrder));
        given(orderItemRepository.findAllBySellerOrderIdOrderByIdAsc(
                SELLER_ORDER_ID
        )).willReturn(List.of(orderItem));
        OrderCancellation cancellation = org.mockito.Mockito.mock(OrderCancellation.class);
        given(cancellation.getId()).willReturn(60L);
        given(cancellation.getStatus()).willReturn(OrderCancellationStatus.COMPLETED);
        given(cancellation.getRequestedAt()).willReturn(LocalDateTime.now());
        given(orderCancellationRepository
                .findAllBySellerOrderIdAndRequiresSellerApprovalTrueOrderByRequestedAtDescIdDesc(
                        SELLER_ORDER_ID
                )).willReturn(List.of(cancellation));

        SellerOrderDetailResponse response = service.getSellerOrder(
                USER_ID, SELLER_ORDER_ID
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().productName()).isEqualTo("테스트 상품");
        assertThat(response.items().getFirst().quantity()).isEqualTo(2);
        assertThat(response.items().getFirst().canceledQuantity()).isEqualTo(1);
        assertThat(response.items().getFirst().remainingQuantity()).isEqualTo(1);
        assertThat(response.cancellations()).singleElement().satisfies(summary -> {
            assertThat(summary.cancellationId()).isEqualTo(60L);
            assertThat(summary.status()).isEqualTo(OrderCancellationStatus.COMPLETED);
        });
        verify(orderItemRepository).findAllBySellerOrderIdOrderByIdAsc(
                SELLER_ORDER_ID
        );
    }

    @Test
    void anotherSellerCannotReadOrChangeSellerOrder() {
        given(sellerOrderRepository.findByIdAndSellerId(
                SELLER_ORDER_ID, SELLER_ID
        )).willReturn(Optional.empty());
        given(sellerOrderRepository.findOrderIdByIdAndSellerId(
                SELLER_ORDER_ID, SELLER_ID
        )).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSellerOrder(USER_ID, SELLER_ORDER_ID))
                .isInstanceOf(SellerException.class);
        assertThatThrownBy(() -> service.prepare(USER_ID, SELLER_ORDER_ID))
                .isInstanceOf(SellerException.class);
        verify(orderRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void preparesShipsAndDeliversOwnedSellerOrder() {
        givenLockedSellerOrder();

        SellerOrderDetailResponse prepared = service.prepare(USER_ID, SELLER_ORDER_ID);
        SellerOrderDetailResponse shipped = service.ship(
                USER_ID,
                SELLER_ORDER_ID,
                new SellerOrderShipRequest(" 테스트택배 ", " 1234567890 ")
        );
        SellerOrderDetailResponse delivered = service.deliver(
                USER_ID, SELLER_ORDER_ID
        );

        assertThat(prepared.status()).isEqualTo(SellerOrderStatus.PREPARING);
        assertThat(prepared.preparedAt()).isNotNull();
        assertThat(shipped.status()).isEqualTo(SellerOrderStatus.SHIPPED);
        assertThat(shipped.shippingCompany()).isEqualTo("테스트택배");
        assertThat(shipped.trackingNumber()).isEqualTo("1234567890");
        assertThat(shipped.shippedAt()).isNotNull();
        assertThat(delivered.status()).isEqualTo(SellerOrderStatus.DELIVERED);
        assertThat(delivered.deliveredAt()).isNotNull();
    }

    @Test
    void changingOneSellerOrderDoesNotChangeAnotherSellerOrder() {
        Seller otherSeller = org.mockito.Mockito.mock(Seller.class);
        SellerOrder other = SellerOrder.createPendingPayment(order, otherSeller);
        other.markPaid();
        givenLockedSellerOrder();

        service.prepare(USER_ID, SELLER_ORDER_ID);

        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.PREPARING);
        assertThat(other.getStatus()).isEqualTo(SellerOrderStatus.PAID);
    }

    @Test
    void cancelledSellerOrderCannotBePreparedEvenAfterLockWait() {
        sellerOrder.cancel();
        givenLockedSellerOrder();

        assertThatThrownBy(() -> service.prepare(USER_ID, SELLER_ORDER_ID))
                .isInstanceOf(SellerException.class);
        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.CANCELLED);
    }

    @Test
    void activeCancellationBlocksShippingAfterSellerOrderLock() {
        sellerOrder.prepare(java.time.LocalDateTime.now());
        givenLockedSellerOrder();
        given(orderCancellationRepository
                .existsBySellerOrderIdAndStatusIn(
                        eq(SELLER_ORDER_ID),
                        any()
                ))
                .willReturn(true);

        assertThatThrownBy(() -> service.ship(
                USER_ID,
                SELLER_ORDER_ID,
                new SellerOrderShipRequest("택배사", "1234")
        )).isInstanceOf(SellerException.class);

        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.PREPARING);
    }

    @Test
    void shippingContinuesWhenNoActiveCancellationExists() {
        sellerOrder.prepare(java.time.LocalDateTime.now());
        givenLockedSellerOrder();
        given(orderCancellationRepository
                .existsBySellerOrderIdAndStatusIn(
                        eq(SELLER_ORDER_ID),
                        any()
                ))
                .willReturn(false);

        SellerOrderDetailResponse response = service.ship(
                USER_ID,
                SELLER_ORDER_ID,
                new SellerOrderShipRequest("택배사", "1234")
        );

        assertThat(response.status()).isEqualTo(SellerOrderStatus.SHIPPED);
    }

    private void givenLockedSellerOrder() {
        given(sellerOrderRepository.findOrderIdByIdAndSellerId(
                SELLER_ORDER_ID, SELLER_ID
        )).willReturn(Optional.of(ORDER_ID));
        given(orderRepository.findByIdForUpdate(ORDER_ID))
                .willReturn(Optional.of(order));
        given(sellerOrderRepository.findByIdAndSellerIdForUpdate(
                SELLER_ORDER_ID, SELLER_ID
        )).willReturn(Optional.of(sellerOrder));
        lenient().when(orderItemRepository.findAllBySellerOrderIdOrderByIdAsc(
                SELLER_ORDER_ID
        )).thenReturn(List.of(orderItem));
    }

    private OrderItem orderItem() {
        OrderItem item = org.mockito.Mockito.mock(OrderItem.class);
        Product product = org.mockito.Mockito.mock(Product.class);
        lenient().when(item.getId()).thenReturn(40L);
        lenient().when(item.getProduct()).thenReturn(product);
        lenient().when(product.getId()).thenReturn(50L);
        lenient().when(item.getProductName()).thenReturn("테스트 상품");
        lenient().when(item.getProductPrice()).thenReturn(10_000L);
        lenient().when(item.getAdditionalPrice()).thenReturn(0L);
        lenient().when(item.getUnitPrice()).thenReturn(10_000L);
        lenient().when(item.getQuantity()).thenReturn(2);
        lenient().when(item.getCanceledQuantity()).thenReturn(1);
        lenient().when(item.getRemainingQuantity()).thenReturn(1);
        lenient().when(item.getTotalPrice()).thenReturn(20_000L);
        lenient().when(item.getShippingFee()).thenReturn(0L);
        return item;
    }

    private SellerOrderItemSummaryProjection summary() {
        SellerOrderItemSummaryProjection summary =
                org.mockito.Mockito.mock(SellerOrderItemSummaryProjection.class);
        given(summary.getSellerOrderId()).willReturn(SELLER_ORDER_ID);
        given(summary.getRepresentativeProductName()).willReturn("테스트 상품");
        given(summary.getProductTypeCount()).willReturn(1L);
        given(summary.getTotalQuantity()).willReturn(1L);
        given(summary.getTotalProductAmount()).willReturn(10_000L);
        return summary;
    }
}
