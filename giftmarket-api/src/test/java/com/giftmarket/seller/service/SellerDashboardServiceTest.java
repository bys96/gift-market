package com.giftmarket.seller.service;

import com.giftmarket.order.entity.ExchangeRequestStatus;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.order.repository.SellerOrderItemSummaryProjection;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.seller.dto.response.SellerDashboardResponse;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SellerDashboardServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SELLER_ID = 10L;

    @Mock SellerRepository sellerRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderCancellationRepository orderCancellationRepository;
    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock ExchangeRequestRepository exchangeRequestRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock Seller seller;

    private SellerDashboardService service;

    @BeforeEach
    void setUp() {
        service = new SellerDashboardService(
                sellerRepository,
                sellerOrderRepository,
                orderCancellationRepository,
                returnRequestRepository,
                exchangeRequestRepository,
                productRepository,
                orderItemRepository
        );
        given(sellerRepository.findByUserId(USER_ID)).willReturn(Optional.of(seller));
        lenient().when(seller.getId()).thenReturn(SELLER_ID);
        given(seller.getStatus()).willReturn(SellerStatus.ACTIVE);
        lenient().when(seller.getStoreName()).thenReturn("선물 상점");
        lenient().when(sellerOrderRepository.findRecentSellerOrders(
                eq(SELLER_ID), eq(SellerOrderStatus.PENDING_PAYMENT), any()
        )).thenReturn(List.of());
    }

    @Test
    void aggregatesOnlyStatesThatHaveCurrentSellerActions() {
        given(sellerOrderRepository.countBySellerIdAndStatusIn(eq(SELLER_ID), any()))
                .willReturn(3L);
        given(orderCancellationRepository
                .countBySellerOrderSellerIdAndRequiresSellerApprovalTrueAndStatus(
                        SELLER_ID, OrderCancellationStatus.REQUESTED
                )).willReturn(1L);
        given(returnRequestRepository.countBySellerOrderSellerIdAndStatus(
                SELLER_ID, ReturnRequestStatus.REQUESTED
        )).willReturn(1L);
        given(returnRequestRepository.countBySellerOrderSellerIdAndStatus(
                SELLER_ID, ReturnRequestStatus.APPROVED
        )).willReturn(2L);
        given(returnRequestRepository.countBySellerOrderSellerIdAndStatus(
                SELLER_ID, ReturnRequestStatus.COLLECTING
        )).willReturn(3L);
        given(returnRequestRepository.countBySellerOrderSellerIdAndStatus(
                SELLER_ID, ReturnRequestStatus.RECEIVED
        )).willReturn(4L);
        given(exchangeRequestRepository.countBySellerOrderSellerIdAndStatus(
                SELLER_ID, ExchangeRequestStatus.REQUESTED
        )).willReturn(1L);
        given(exchangeRequestRepository.countBySellerOrderSellerIdAndStatus(
                SELLER_ID, ExchangeRequestStatus.COLLECTING
        )).willReturn(2L);
        given(exchangeRequestRepository.countBySellerOrderSellerIdAndStatus(
                SELLER_ID, ExchangeRequestStatus.RECEIVED
        )).willReturn(3L);
        given(exchangeRequestRepository.countBySellerOrderSellerIdAndStatus(
                SELLER_ID, ExchangeRequestStatus.INSPECTED
        )).willReturn(4L);
        given(exchangeRequestRepository.countBySellerOrderSellerIdAndStatus(
                SELLER_ID, ExchangeRequestStatus.RESHIPPING
        )).willReturn(5L);
        given(sellerOrderRepository.findRecentSellerOrders(
                eq(SELLER_ID), eq(SellerOrderStatus.PENDING_PAYMENT), any()
        )).willReturn(List.of());

        SellerDashboardResponse response = service.getDashboard(USER_ID);

        assertThat(response.actionRequired().orders()).isEqualTo(3L);
        assertThat(response.storeName()).isEqualTo("선물 상점");
        assertThat(response.actionRequired().cancellations()).isEqualTo(1L);
        assertThat(response.actionRequired().returns().total()).isEqualTo(10L);
        assertThat(response.actionRequired().exchanges().total()).isEqualTo(15L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<SellerOrderStatus>> statuses =
                ArgumentCaptor.forClass(Collection.class);
        verify(sellerOrderRepository).countBySellerIdAndStatusIn(
                eq(SELLER_ID), statuses.capture()
        );
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                SellerOrderStatus.PAID,
                SellerOrderStatus.PREPARING,
                SellerOrderStatus.SHIPPED
        );
        verify(exchangeRequestRepository, never())
                .countBySellerOrderSellerIdAndStatus(
                        SELLER_ID, ExchangeRequestStatus.PAYMENT_PENDING
                );
        verify(returnRequestRepository, never())
                .countBySellerOrderSellerIdAndStatus(
                        SELLER_ID, ReturnRequestStatus.COMPLETED
                );
        verify(exchangeRequestRepository, never())
                .countBySellerOrderSellerIdAndStatus(
                        SELLER_ID, ExchangeRequestStatus.COMPLETED
                );
    }

    @Test
    void providesProductCountsAndFiveMostRecentOrdersWithoutLoadingAllOrders() {
        SellerOrder sellerOrder = org.mockito.Mockito.mock(SellerOrder.class);
        Order order = org.mockito.Mockito.mock(Order.class);
        SellerOrderItemSummaryProjection summary =
                org.mockito.Mockito.mock(SellerOrderItemSummaryProjection.class);
        LocalDateTime orderedAt = LocalDateTime.of(2026, 8, 25, 12, 0);

        given(productRepository.countBySellerIdAndStatusAndDeletedAtIsNull(
                SELLER_ID, ProductStatus.ON_SALE
        )).willReturn(7L);
        given(productRepository.countBySellerIdAndStatusAndDeletedAtIsNull(
                SELLER_ID, ProductStatus.SOLD_OUT
        )).willReturn(2L);
        given(sellerOrderRepository.findRecentSellerOrders(
                eq(SELLER_ID), eq(SellerOrderStatus.PENDING_PAYMENT), any()
        )).willReturn(List.of(sellerOrder));
        given(sellerOrder.getId()).willReturn(30L);
        given(sellerOrder.getOrder()).willReturn(order);
        given(sellerOrder.getStatus()).willReturn(SellerOrderStatus.PAID);
        given(order.getId()).willReturn(20L);
        given(order.getOrderNumber()).willReturn("GM-ORDER-20");
        given(order.getOrderedAt()).willReturn(orderedAt);
        given(orderItemRepository.summarizeBySellerOrderIds(List.of(30L)))
                .willReturn(List.of(summary));
        given(summary.getSellerOrderId()).willReturn(30L);
        given(summary.getRepresentativeProductName()).willReturn("선물 상품");
        given(summary.getProductTypeCount()).willReturn(3L);
        given(summary.getTotalQuantity()).willReturn(4L);
        given(summary.getTotalProductAmount()).willReturn(50_000L);

        SellerDashboardResponse response = service.getDashboard(USER_ID);

        assertThat(response.products().onSale()).isEqualTo(7L);
        assertThat(response.products().soldOut()).isEqualTo(2L);
        assertThat(response.recentOrders()).singleElement().satisfies(recent -> {
            assertThat(recent.sellerOrderId()).isEqualTo(30L);
            assertThat(recent.additionalProductCount()).isEqualTo(2L);
            assertThat(recent.totalProductAmount()).isEqualTo(50_000L);
            assertThat(recent.orderedAt()).isEqualTo(orderedAt);
        });

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sellerOrderRepository).findRecentSellerOrders(
                eq(SELLER_ID), eq(SellerOrderStatus.PENDING_PAYMENT), pageable.capture()
        );
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    @Test
    void rejectsInactiveSellerBeforeAnyDashboardQuery() {
        given(seller.getStatus()).willReturn(SellerStatus.SUSPENDED);

        assertThatThrownBy(() -> service.getDashboard(USER_ID))
                .isInstanceOf(SellerException.class);

        verify(sellerOrderRepository, never()).countBySellerIdAndStatusIn(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = SellerOrderStatus.class, names = {
            "PENDING_PAYMENT", "DELIVERED", "CANCELLED"
    })
    void excludesOrdersWithoutSellerAction(SellerOrderStatus excludedStatus) {
        service.getDashboard(USER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<SellerOrderStatus>> statuses =
                ArgumentCaptor.forClass(Collection.class);
        verify(sellerOrderRepository).countBySellerIdAndStatusIn(
                eq(SELLER_ID), statuses.capture()
        );
        assertThat(statuses.getValue()).doesNotContain(excludedStatus);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnRequestStatus.class, names = {
            "INSPECTED", "REFUNDING", "COMPLETED", "REJECTED", "CANCELED", "FAILED"
    })
    void excludesReturnsWithoutSellerAction(ReturnRequestStatus excludedStatus) {
        service.getDashboard(USER_ID);

        verify(returnRequestRepository, never())
                .countBySellerOrderSellerIdAndStatus(SELLER_ID, excludedStatus);
    }

    @ParameterizedTest
    @EnumSource(value = ExchangeRequestStatus.class, names = {
            "APPROVED", "PAYMENT_PENDING", "COMPLETED", "REJECTED", "CANCELED", "FAILED"
    })
    void excludesExchangesWithoutSellerAction(ExchangeRequestStatus excludedStatus) {
        service.getDashboard(USER_ID);

        verify(exchangeRequestRepository, never())
                .countBySellerOrderSellerIdAndStatus(SELLER_ID, excludedStatus);
    }
}
