package com.giftmarket.order.service;

import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.order.dto.request.ExchangeRequestCreateRequest;
import com.giftmarket.order.dto.request.ExchangeRequestItemRequest;
import com.giftmarket.order.dto.response.ExchangeRequestResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.*;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.ProductVariantOptionValueRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
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
class ExchangeRequestServiceTest {
    private static final long USER_ID = 1L;
    private static final long ORDER_ID = 10L;
    private static final long SELLER_ORDER_ID = 20L;
    private static final long ORDER_ITEM_ID = 30L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 12, 0);

    @Mock OrderRepository orderRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ShipmentRepository shipmentRepository;
    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock ExchangeRequestRepository exchangeRequestRepository;
    @Mock ExchangeRequestItemRepository exchangeRequestItemRepository;
    @Mock ExchangeRequestImageRepository exchangeRequestImageRepository;
    @Mock ProductVariantRepository productVariantRepository;
    @Mock ProductVariantOptionValueRepository productVariantOptionValueRepository;
    @Mock StorageService storageService;

    private ExchangeRequestService service;
    private Order order;
    private SellerOrder sellerOrder;
    private OrderItem orderItem;
    private Product product;

    @BeforeEach
    void setUp() {
        service = spy(new ExchangeRequestService(
                orderRepository, sellerOrderRepository, orderItemRepository, shipmentRepository,
                returnRequestRepository, exchangeRequestRepository, exchangeRequestItemRepository,
                exchangeRequestImageRepository, productVariantRepository,
                productVariantOptionValueRepository, storageService
        ));
        doReturn(NOW).when(service).currentTime();
        User user = mock(User.class);
        given(user.getId()).willReturn(USER_ID);
        order = Order.createPendingPayment("GM-ORDER", user, 30_000L, 0L,
                "수령인", "010-1234-5678", "12345", "서울", null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.markPaid(NOW.minusDays(10));
        sellerOrder = SellerOrder.createPendingPayment(order, mock(Seller.class));
        ReflectionTestUtils.setField(sellerOrder, "id", SELLER_ORDER_ID);
        sellerOrder.markPaid();
        sellerOrder.prepare(NOW.minusDays(3));
        sellerOrder.markShipped(NOW.minusDays(2));
        sellerOrder.markDelivered(NOW.minusDays(1));
        product = mock(Product.class);
        given(product.getId()).willReturn(40L);
        given(product.getName()).willReturn("상품");
        given(product.getPrice()).willReturn(10_000L);
        given(product.getStockQuantity()).willReturn(10);
        given(product.getStatus()).willReturn(ProductStatus.ON_SALE);
        orderItem = OrderItem.create(order, product, null, mock(Seller.class), sellerOrder, null,
                "상품", null, "상점", null, null, 10_000L, 0L,
                3, true, 0L, 3_000L, 6_000L);
        ReflectionTestUtils.setField(orderItem, "id", ORDER_ITEM_ID);

        given(exchangeRequestRepository.findByClientRequestKey(anyString())).willReturn(Optional.empty());
        given(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID)).willReturn(Optional.of(order));
        given(sellerOrderRepository.findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID))
                .willReturn(Optional.of(sellerOrder));
        given(orderItemRepository.findAllByIdInForUpdate(List.of(ORDER_ITEM_ID))).willReturn(List.of(orderItem));
        given(shipmentRepository.findBySellerOrderIdAndType(SELLER_ORDER_ID, ShipmentType.ORIGINAL_OUTBOUND))
                .willReturn(Optional.of(deliveredShipment(NOW.minusDays(1))));
        given(returnRequestRepository.sumItemQuantitiesByStatuses(any(), any())).willReturn(List.of());
        given(exchangeRequestRepository.sumItemQuantitiesByStatuses(any(), any())).willReturn(List.of());
        given(exchangeRequestRepository.saveAndFlush(any())).willAnswer(invocation -> {
            ExchangeRequest value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 100L);
            return value;
        });
        given(exchangeRequestItemRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsDeliveredOptionlessExchangeWithoutMutatingStockOrCompletedQuantity() {
        int productStock = product.getStockQuantity();
        ExchangeRequestResponse response = create(request(ExchangeReasonType.CHANGE_OF_MIND, 2, List.of()));

        assertThat(response.status()).isEqualTo(ExchangeRequestStatus.REQUESTED);
        assertThat(response.responsibility()).isEqualTo(ExchangeResponsibility.BUYER);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.targetUnitPrice()).isEqualTo(10_000L);
        });
        assertThat(product.getStockQuantity()).isEqualTo(productStock);
        assertThat(orderItem.getExchangedQuantity()).isZero();
        assertThat(response.collectionShipment()).isNull();
        assertThat(response.outboundShipment()).isNull();
    }

    @Test
    void appliesExactBuyerBoundaryButNotSellerOrOtherDeadline() {
        given(shipmentRepository.findBySellerOrderIdAndType(anyLong(), any()))
                .willReturn(Optional.of(deliveredShipment(NOW.minusDays(7))));
        assertThat(create(request(ExchangeReasonType.CHANGE_OF_MIND, 1, List.of()))).isNotNull();

        given(shipmentRepository.findBySellerOrderIdAndType(anyLong(), any()))
                .willReturn(Optional.of(deliveredShipment(NOW.minusDays(7).minusNanos(1))));
        assertThatThrownBy(() -> create(request(ExchangeReasonType.OPTION_MISTAKE, 1, List.of())))
                .isInstanceOf(OrderException.class).hasMessageContaining("기간");
        assertThat(create(request(ExchangeReasonType.DEFECTIVE, 1, List.of()))).isNotNull();
        assertThat(create(request(ExchangeReasonType.OTHER, 1, List.of())).responsibility()).isNull();
    }

    @Test
    void subtractsCompletedReturnExchangeAndBothActiveReservations() {
        ReflectionTestUtils.setField(orderItem, "canceledQuantity", 1);
        PendingReturnQuantityProjection returnPending = mock(PendingReturnQuantityProjection.class);
        given(returnPending.getOrderItemId()).willReturn(ORDER_ITEM_ID);
        given(returnPending.getPendingQuantity()).willReturn(1L);
        PendingExchangeQuantityProjection exchangePending = mock(PendingExchangeQuantityProjection.class);
        given(exchangePending.getOrderItemId()).willReturn(ORDER_ITEM_ID);
        given(exchangePending.getPendingQuantity()).willReturn(1L);
        given(returnRequestRepository.sumItemQuantitiesByStatuses(any(), any())).willReturn(List.of(returnPending));
        given(exchangeRequestRepository.sumItemQuantitiesByStatuses(any(), any())).willReturn(List.of(exchangePending));

        assertThatThrownBy(() -> create(request(ExchangeReasonType.DEFECTIVE, 1, List.of())))
                .isInstanceOf(OrderException.class).hasMessageContaining("교환 가능 수량");
        @SuppressWarnings("unchecked") var statuses = org.mockito.ArgumentCaptor.forClass(Set.class);
        verify(exchangeRequestRepository).sumItemQuantitiesByStatuses(any(), statuses.capture());
        assertThat(statuses.getValue()).contains(ExchangeRequestStatus.PAYMENT_PENDING,
                ExchangeRequestStatus.RESHIPPING).doesNotContain(ExchangeRequestStatus.COMPLETED);
    }

    @Test
    void validatesImagePrefixDuplicatesAndPersistsFiveInOrder() {
        List<String> keys = List.of("exchanges/1/a.jpg", "exchanges/1/b.jpg", "exchanges/1/c.jpg",
                "exchanges/1/d.jpg", "exchanges/1/e.jpg");
        assertThat(create(request(ExchangeReasonType.DEFECTIVE, 1, keys)).images()).hasSize(5);
        @SuppressWarnings("unchecked")
        var images = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(exchangeRequestImageRepository).saveAll(images.capture());
        assertThat((List<ExchangeRequestImage>) images.getValue()).hasSize(5)
                .extracting(ExchangeRequestImage::getSortOrder).containsExactly(0, 1, 2, 3, 4);

        assertThatThrownBy(() -> create(request(ExchangeReasonType.DEFECTIVE, 1,
                List.of("exchanges/2/a.jpg")))).isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> create(request(ExchangeReasonType.DEFECTIVE, 1,
                List.of("exchanges/1/a.jpg", "exchanges/1/a.jpg"))))
                .isInstanceOf(OrderException.class).hasMessageContaining("중복");
    }

    @Test
    void locksInOrderAndConvertsUniqueRaceToDomainConflict() {
        create(request(ExchangeReasonType.DEFECTIVE, 1, List.of()));
        InOrder locks = inOrder(orderRepository, sellerOrderRepository, orderItemRepository,
                returnRequestRepository, exchangeRequestRepository);
        locks.verify(orderRepository).findByIdAndUserIdForUpdate(ORDER_ID, USER_ID);
        locks.verify(sellerOrderRepository).findByIdAndOrderIdForUpdate(SELLER_ORDER_ID, ORDER_ID);
        locks.verify(orderItemRepository).findAllByIdInForUpdate(List.of(ORDER_ITEM_ID));
        locks.verify(returnRequestRepository).sumItemQuantitiesByStatuses(any(), any());
        locks.verify(exchangeRequestRepository).sumItemQuantitiesByStatuses(any(), any());

        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(exchangeRequestRepository).saveAndFlush(any());
        assertThatThrownBy(() -> create(request(ExchangeReasonType.DEFECTIVE, 1, List.of())))
                .isInstanceOf(OrderException.class).hasMessageContaining("이미 사용된");
    }

    private ExchangeRequestResponse create(ExchangeRequestCreateRequest request) {
        return service.create(USER_ID, ORDER_ID, SELLER_ORDER_ID, request);
    }

    private ExchangeRequestCreateRequest request(ExchangeReasonType reason, int quantity, List<String> keys) {
        return new ExchangeRequestCreateRequest(
                UUID.randomUUID().toString(), reason, " 교환 사유 ", " 구매자 ", " 010-1234-5678 ",
                " 12345 ", " 서울 ", " 101호 ", " 구매자 ", " 010-1234-5678 ",
                " 12345 ", " 서울 ", " 101호 ",
                List.of(new ExchangeRequestItemRequest(ORDER_ITEM_ID, quantity, null)), keys
        );
    }

    private Shipment deliveredShipment(LocalDateTime deliveredAt) {
        Shipment value = Shipment.createShipped(sellerOrder, ShipmentType.ORIGINAL_OUTBOUND,
                "택배", UUID.randomUUID().toString(), deliveredAt.minusDays(1));
        value.deliver(deliveredAt);
        return value;
    }
}
