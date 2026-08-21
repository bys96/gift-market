package com.giftmarket.order.service;

import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.cart.repository.CartItemRepository;
import com.giftmarket.order.dto.request.DirectOrderCreateRequest;
import com.giftmarket.order.dto.request.OrderCreateRequest;
import com.giftmarket.order.dto.response.OrderCreateResponse;
import com.giftmarket.order.dto.response.BuyerOrderDeliveryStatus;
import com.giftmarket.order.dto.response.OrderDetailResponse;
import com.giftmarket.order.dto.response.OrderSummaryResponse;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.PendingCancellationQuantityProjection;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.order.repository.ShipmentRepository;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.payment.service.PaymentRefundBalance;
import com.giftmarket.payment.service.PaymentRefundBalanceService;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductOptionGroup;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.ProductOptionGroupRepository;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.product.repository.ProductVariantOptionValueRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class OrderServicePaymentPreparationTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long CART_ITEM_ID = 100L;
    private static final Long SELLER_ID = 20L;
    private static final String REQUEST_KEY =
            "123e4567-e89b-42d3-a456-426614174000";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderCancellationRepository orderCancellationRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentRefundBalanceService paymentRefundBalanceService;

    @Mock
    private PaymentProperties paymentProperties;

    @Mock
    private SellerOrderLifecycleService sellerOrderLifecycleService;

    @Mock
    private SellerOrderRepository sellerOrderRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductOptionGroupRepository productOptionGroupRepository;

    @Mock
    private ProductVariantOptionValueRepository
            productVariantOptionValueRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderService orderService;

    @Mock
    private User user;

    @Mock
    private Product product;

    @Mock
    private Seller seller;

    @Mock
    private CartItem cartItem;

    @Mock
    private ProductOptionGroup productOptionGroup;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));
        lenient().when(paymentProperties.getReservationMinutes())
                .thenReturn(30L);
        lenient().when(seller.getId()).thenReturn(SELLER_ID);
        lenient().when(sellerOrderLifecycleService.createPendingPayment(
                        any(Order.class),
                        any()
                ))
                .thenAnswer(invocation -> Map.of(
                        SELLER_ID,
                        SellerOrder.createPendingPayment(
                                invocation.getArgument(0),
                                seller
                        )
                ));
    }

    @Test
    void preparesCartOrderAndKeepsCartItem() {
        given(paymentRepository
                .findByClientRequestKeyAndOrderUserId(
                        REQUEST_KEY,
                        USER_ID
                ))
                .willReturn(Optional.empty());
        preparePurchasableCartItem();

        OrderCreateResponse response = orderService.createOrder(
                USER_ID,
                createCartRequest(REQUEST_KEY)
        );

        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        ArgumentCaptor<List<OrderItem>> itemsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(itemsCaptor.capture());

        ArgumentCaptor<Payment> paymentCaptor =
                ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());

        Order order = orderCaptor.getValue();
        OrderItem orderItem = itemsCaptor.getValue().getFirst();
        Payment payment = paymentCaptor.getValue();

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getOrderedAt()).isNull();
        assertThat(orderItem.getSourceCartItemId())
                .isEqualTo(CART_ITEM_ID);
        assertThat(orderItem.getReturnShippingFee())
                .isEqualTo(3_000L);
        assertThat(orderItem.getExchangeShippingFee())
                .isEqualTo(6_000L);
        assertThat(orderItem.getSellerOrder()).isNotNull();
        assertThat(orderItem.getSellerOrder().getStatus())
                .isEqualTo(com.giftmarket.order.entity.SellerOrderStatus.PENDING_PAYMENT);
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.READY);
        assertThat(payment.getOrder()).isSameAs(order);
        assertThat(payment.getAmount()).isEqualTo(10_000L);
        assertThat(payment.getExpiresAt())
                .isEqualTo(payment.getRequestedAt().plusMinutes(30));
        assertThat(response.status())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.paymentStatus())
                .isEqualTo(PaymentStatus.READY);

        verify(product).decreaseStock(2);
        verify(cartItemRepository, never())
                .deleteAllByIdInAndUserId(any(), any());
    }

    @Test
    void returnsExistingPreparationForSameRequestKey() {
        given(paymentRepository
                .findByClientRequestKeyAndOrderUserId(
                        REQUEST_KEY,
                        USER_ID
                ))
                .willReturn(Optional.empty());
        preparePurchasableCartItem();

        OrderCreateResponse firstResponse = orderService.createOrder(
                USER_ID,
                createCartRequest(REQUEST_KEY)
        );

        ArgumentCaptor<Payment> paymentCaptor =
                ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());

        ArgumentCaptor<List<OrderItem>> itemsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(itemsCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();
        List<OrderItem> savedItems = itemsCaptor.getValue();

        given(paymentRepository
                .findByClientRequestKeyAndOrderUserId(
                        REQUEST_KEY,
                        USER_ID
                ))
                .willReturn(Optional.of(savedPayment));
        given(orderItemRepository.findAllByOrderIdOrderByIdAsc(any()))
                .willReturn(savedItems);

        OrderCreateResponse retriedResponse = orderService.createOrder(
                USER_ID,
                createCartRequest(REQUEST_KEY)
        );

        assertThat(retriedResponse.orderNumber())
                .isEqualTo(firstResponse.orderNumber());
        assertThat(retriedResponse.merchantPaymentId())
                .isEqualTo(firstResponse.merchantPaymentId());
        verify(orderRepository, times(1)).save(any());
        verify(paymentRepository, times(1)).save(any());
        verify(sellerOrderLifecycleService, times(1))
                .createPendingPayment(any(Order.class), any());
        verify(product, times(1)).decreaseStock(2);
    }

    @Test
    void differentRequestKeyCreatesAnotherPreparation() {
        String otherRequestKey =
                "223e4567-e89b-42d3-a456-426614174001";

        given(paymentRepository
                .findByClientRequestKeyAndOrderUserId(
                        any(),
                        any()
                ))
                .willReturn(Optional.empty());
        preparePurchasableCartItem();

        orderService.createOrder(
                USER_ID,
                createCartRequest(REQUEST_KEY)
        );
        orderService.createOrder(
                USER_ID,
                createCartRequest(otherRequestKey)
        );

        verify(orderRepository, times(2)).save(any());
        verify(paymentRepository, times(2)).save(any());
        verify(product, times(2)).decreaseStock(2);
    }

    @Test
    void preparesDirectOrderWithoutCartSource() {
        given(paymentRepository
                .findByClientRequestKeyAndOrderUserId(
                        REQUEST_KEY,
                        USER_ID
                ))
                .willReturn(Optional.empty());
        preparePurchasableProduct();
        given(productRepository.findWithLockByIdAndDeletedAtIsNull(PRODUCT_ID))
                .willReturn(Optional.of(product));

        OrderCreateResponse response = orderService.createDirectOrder(
                USER_ID,
                createDirectRequest()
        );

        ArgumentCaptor<OrderItem> itemCaptor =
                ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository).save(itemCaptor.capture());

        assertThat(itemCaptor.getValue().getSourceCartItemId())
                .isNull();
        assertThat(itemCaptor.getValue().getReturnShippingFee())
                .isEqualTo(3_000L);
        assertThat(itemCaptor.getValue().getExchangeShippingFee())
                .isEqualTo(6_000L);
        assertThat(itemCaptor.getValue().getSellerOrder()).isNotNull();
        assertThat(itemCaptor.getValue().getSellerOrder().getSeller())
                .isSameAs(seller);
        assertThat(response.status())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.paymentStatus())
                .isEqualTo(PaymentStatus.READY);
        verify(product).decreaseStock(2);
        verify(cartItemRepository, never())
                .deleteAllByIdInAndUserId(any(), any());
    }

    @Test
    void soldOutProductDoesNotCreatePreparation() {
        given(paymentRepository
                .findByClientRequestKeyAndOrderUserId(
                        REQUEST_KEY,
                        USER_ID
                ))
                .willReturn(Optional.empty());
        given(productRepository.findWithLockByIdAndDeletedAtIsNull(PRODUCT_ID))
                .willReturn(Optional.of(product));
        given(product.isDeleted()).willReturn(false);
        given(product.getStatus()).willReturn(ProductStatus.SOLD_OUT);

        assertThatThrownBy(() ->
                orderService.createDirectOrder(
                        USER_ID,
                        createDirectRequest()
                )
        ).isInstanceOf(OrderException.class);

        verify(orderRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verify(product, never()).decreaseStock(any());
    }

    @Test
    void stoppedProductDoesNotCreatePreparation() {
        given(paymentRepository
                .findByClientRequestKeyAndOrderUserId(
                        REQUEST_KEY,
                        USER_ID
                ))
                .willReturn(Optional.empty());
        given(productRepository.findWithLockByIdAndDeletedAtIsNull(PRODUCT_ID))
                .willReturn(Optional.of(product));
        given(product.isDeleted()).willReturn(false);
        given(product.getStatus()).willReturn(ProductStatus.HIDDEN);

        assertThatThrownBy(() ->
                orderService.createDirectOrder(
                        USER_ID,
                        createDirectRequest()
                )
        ).isInstanceOf(OrderException.class);

        verify(orderRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verify(product, never()).decreaseStock(any());
    }

    @Test
    void missingRequiredOptionDoesNotCreatePreparation() {
        given(paymentRepository
                .findByClientRequestKeyAndOrderUserId(
                        REQUEST_KEY,
                        USER_ID
                ))
                .willReturn(Optional.empty());
        given(productRepository.findWithLockByIdAndDeletedAtIsNull(PRODUCT_ID))
                .willReturn(Optional.of(product));
        given(product.getId()).willReturn(PRODUCT_ID);
        given(product.isDeleted()).willReturn(false);
        given(product.getStatus()).willReturn(ProductStatus.ON_SALE);
        given(productOptionGroupRepository
                .findAllByProductIdOrderBySortOrderAsc(PRODUCT_ID))
                .willReturn(List.of(productOptionGroup));

        assertThatThrownBy(() ->
                orderService.createDirectOrder(
                        USER_ID,
                        createDirectRequest()
                )
        ).isInstanceOf(OrderException.class);

        verify(orderRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verify(product, never()).decreaseStock(any());
    }

    @Test
    void legacyOrderedOrderStillSupportsCancellation() {
        Order order = Order.create(
                "GM-20260815-LEGACY000001",
                user,
                10_000L,
                0L,
                "받는 사람",
                "010-1234-5678",
                "12345",
                "서울시 테스트로",
                null
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(order.getOrderedAt()).isNotNull();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();
    }

    @Test
    void loadsBuyerOrderListAssociationsInTwoBulkQueries() {
        Order firstOrder = mock(Order.class);
        Order secondOrder = mock(Order.class);
        OrderItem firstItem = mock(OrderItem.class);
        OrderItem secondItem = mock(OrderItem.class);
        SellerOrder firstSellerOrder = mock(SellerOrder.class);
        SellerOrder secondSellerOrder = mock(SellerOrder.class);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        prepareOrderSummary(firstOrder, 1L, OrderStatus.PAID);
        prepareOrderSummary(secondOrder, 2L, OrderStatus.PAID);
        prepareHistoryItem(firstItem, firstOrder, 1001L);
        prepareHistoryItem(secondItem, secondOrder, 1002L);
        given(firstSellerOrder.getOrder()).willReturn(firstOrder);
        given(firstSellerOrder.getStatus()).willReturn(SellerOrderStatus.PREPARING);
        given(secondSellerOrder.getOrder()).willReturn(secondOrder);
        given(secondSellerOrder.getStatus()).willReturn(SellerOrderStatus.SHIPPED);
        given(orderRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(List.of(firstOrder, secondOrder));
        given(orderItemRepository.findAllByOrderIdInOrderByOrderIdAscIdAsc(
                List.of(1L, 2L)
        )).willReturn(List.of(firstItem, secondItem));
        given(sellerOrderRepository.findAllByOrderIdInOrderByOrderIdAscIdAsc(
                List.of(1L, 2L)
        )).willReturn(List.of(firstSellerOrder, secondSellerOrder));

        List<OrderSummaryResponse> responses = orderService.getMyOrders(USER_ID);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).deliveryStatus())
                .isEqualTo(BuyerOrderDeliveryStatus.PREPARING);
        assertThat(responses.get(1).deliveryStatus())
                .isEqualTo(BuyerOrderDeliveryStatus.SHIPPING);
        verify(orderItemRepository, times(1))
                .findAllByOrderIdInOrderByOrderIdAscIdAsc(List.of(1L, 2L));
        verify(sellerOrderRepository, times(1))
                .findAllByOrderIdInOrderByOrderIdAscIdAsc(List.of(1L, 2L));
        verify(orderItemRepository, never()).findAllByOrderIdOrderByIdAsc(any());
    }

    @Test
    void groupsBuyerOrderDetailItemsBySellerOrder() {
        Long orderId = 77L;
        Order order = mock(Order.class);
        Seller secondSeller = mock(Seller.class);
        SellerOrder firstSellerOrder = mock(SellerOrder.class);
        SellerOrder secondSellerOrder = mock(SellerOrder.class);
        OrderItem firstItem = mock(OrderItem.class);
        OrderItem secondItem = mock(OrderItem.class);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        prepareOrderSummary(order, orderId, OrderStatus.PAID);
        given(orderRepository.findByIdAndUserId(orderId, USER_ID))
                .willReturn(Optional.of(order));
        prepareSellerOrder(firstSellerOrder, 501L, order, seller, "첫 스토어");
        prepareSellerOrder(secondSellerOrder, 502L, order, secondSeller, "둘째 스토어");
        prepareHistoryItem(firstItem, order, 2001L);
        prepareHistoryItem(secondItem, order, 2002L);
        given(firstItem.getSellerOrder()).willReturn(firstSellerOrder);
        given(secondItem.getSellerOrder()).willReturn(secondSellerOrder);
        given(orderItemRepository.findAllByOrderIdOrderByIdAsc(orderId))
                .willReturn(List.of(firstItem, secondItem));
        given(sellerOrderRepository.findAllByOrderIdOrderByIdAsc(orderId))
                .willReturn(List.of(firstSellerOrder, secondSellerOrder));
        PendingCancellationQuantityProjection pending = mock(PendingCancellationQuantityProjection.class);
        given(pending.getOrderItemId()).willReturn(2001L);
        given(pending.getPendingQuantity()).willReturn(1L);
        given(orderCancellationRepository.sumItemQuantitiesByStatuses(any(), any()))
                .willReturn(List.of(pending));
        Payment payment = mock(Payment.class);
        given(paymentRepository.findFirstByOrderIdAndOrderUserIdOrderByIdDesc(
                orderId, USER_ID
        )).willReturn(Optional.of(payment));
        given(paymentRefundBalanceService.getBalance(payment))
                .willReturn(new PaymentRefundBalance(30_000L, 10_000L, 5_000L, 15_000L));

        OrderDetailResponse response = orderService.getMyOrder(USER_ID, orderId);

        assertThat(response.sellerOrders()).hasSize(2);
        assertThat(response.sellerOrders().get(0).items())
                .extracting(item -> item.id())
                .containsExactly(2001L);
        assertThat(response.sellerOrders().get(1).items())
                .extracting(item -> item.id())
                .containsExactly(2002L);
        assertThat(response.sellerOrders().get(0).items().getFirst().availableCancellationQuantity())
                .isEqualTo(0);
        assertThat(response.refundedAmount()).isEqualTo(10_000L);
        assertThat(response.remainingPaymentAmount()).isEqualTo(20_000L);
    }

    private void prepareOrderSummary(
            Order order,
            Long orderId,
            OrderStatus status
    ) {
        given(order.getId()).willReturn(orderId);
        given(order.getStatus()).willReturn(status);
        given(order.getOrderNumber()).willReturn("GM-TEST-" + orderId);
        given(order.getTotalProductAmount()).willReturn(10_000L);
        given(order.getTotalShippingFee()).willReturn(0L);
        given(order.getTotalAmount()).willReturn(10_000L);
    }

    private void prepareHistoryItem(
            OrderItem item,
            Order order,
            Long itemId
    ) {
        given(item.getId()).willReturn(itemId);
        lenient().when(item.getOrder()).thenReturn(order);
        given(item.getProduct()).willReturn(product);
        given(product.getId()).willReturn(PRODUCT_ID);
        given(item.getProductName()).willReturn("테스트 상품 " + itemId);
        given(item.getQuantity()).willReturn(1);
        given(item.getUnitPrice()).willReturn(10_000L);
        given(item.getTotalPrice()).willReturn(10_000L);
    }

    private void prepareSellerOrder(
            SellerOrder sellerOrder,
            Long sellerOrderId,
            Order order,
            Seller owner,
            String storeName
    ) {
        given(sellerOrder.getId()).willReturn(sellerOrderId);
        lenient().when(sellerOrder.getOrder()).thenReturn(order);
        given(sellerOrder.getSeller()).willReturn(owner);
        given(sellerOrder.getStatus()).willReturn(SellerOrderStatus.PAID);
        given(owner.getStoreName()).willReturn(storeName);
    }

    private void preparePurchasableCartItem() {
        preparePurchasableProduct();
        given(cartItem.getId()).willReturn(CART_ITEM_ID);
        given(cartItem.getProduct()).willReturn(product);
        given(cartItem.getVariant()).willReturn(null);
        given(cartItem.getQuantity()).willReturn(2);
        given(cartItemRepository.findAllByIdInAndUserId(
                List.of(CART_ITEM_ID),
                USER_ID
        )).willReturn(List.of(cartItem));
        given(productRepository.findWithLockByIdAndDeletedAtIsNull(PRODUCT_ID))
                .willReturn(Optional.of(product));
    }

    private void preparePurchasableProduct() {
        given(product.getId()).willReturn(PRODUCT_ID);
        given(product.isDeleted()).willReturn(false);
        given(product.getStatus()).willReturn(ProductStatus.ON_SALE);
        given(product.getStockQuantity()).willReturn(10);
        given(product.getPrice()).willReturn(5_000L);
        given(product.getName()).willReturn("테스트 상품");
        given(product.getBrandName()).willReturn("테스트 브랜드");
        given(product.getSeller()).willReturn(seller);
        given(product.isFreeShipping()).willReturn(true);
        given(product.getReturnShippingFee()).willReturn(3_000L);
        given(product.getExchangeShippingFee()).willReturn(6_000L);
        given(seller.getStoreName()).willReturn("테스트 스토어");
        given(productOptionGroupRepository
                .findAllByProductIdOrderBySortOrderAsc(PRODUCT_ID))
                .willReturn(List.of());
    }

    private OrderCreateRequest createCartRequest(
            String clientOrderRequestKey
    ) {
        return new OrderCreateRequest(
                clientOrderRequestKey,
                List.of(CART_ITEM_ID),
                "받는 사람",
                "010-1234-5678",
                "12345",
                "서울시 테스트로",
                "101호"
        );
    }

    private DirectOrderCreateRequest createDirectRequest() {
        return new DirectOrderCreateRequest(
                REQUEST_KEY,
                PRODUCT_ID,
                null,
                2,
                "받는 사람",
                "010-1234-5678",
                "12345",
                "서울시 테스트로",
                "101호"
        );
    }
}
