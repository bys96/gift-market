package com.giftmarket.order.service;

import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.cart.repository.CartItemRepository;
import com.giftmarket.order.dto.request.DirectOrderCreateRequest;
import com.giftmarket.order.dto.request.OrderCreateRequest;
import com.giftmarket.order.dto.response.OrderCreateResponse;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.payment.config.PaymentProperties;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
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
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProperties paymentProperties;

    @Mock
    private SellerOrderLifecycleService sellerOrderLifecycleService;

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
