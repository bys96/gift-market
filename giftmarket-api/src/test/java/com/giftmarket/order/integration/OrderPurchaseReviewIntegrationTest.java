package com.giftmarket.order.integration;

import com.giftmarket.order.dto.request.DirectOrderCreateRequest;
import com.giftmarket.order.dto.request.SellerOrderShipRequest;
import com.giftmarket.order.dto.response.OrderCreateResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.*;
import com.giftmarket.order.service.OrderService;
import com.giftmarket.order.service.PurchaseConfirmationQuantities;
import com.giftmarket.order.service.PurchaseConfirmationService;
import com.giftmarket.order.service.SellerOrderManagementService;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentMethod;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.payment.service.PaymentService;
import com.giftmarket.product.entity.*;
import com.giftmarket.product.repository.*;
import com.giftmarket.review.dto.ReviewUpsertRequest;
import com.giftmarket.review.entity.Review;
import com.giftmarket.review.repository.ReviewRepository;
import com.giftmarket.review.service.ReviewService;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-purchase-review;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false",
        "app.jwt.secret=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "app.jwt.refresh-token-encryption-key=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "payment.toss.secret-key=test-only-key"
})
@Transactional
class OrderPurchaseReviewIntegrationTest {

    private static final int INITIAL_STOCK = 10;
    private static final int ORDER_QUANTITY = 2;
    private static final long PRODUCT_PRICE = 10_000L;
    private static final long ADDITIONAL_PRICE = 1_000L;
    private static final long SHIPPING_FEE = 3_000L;

    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired SellerOrderManagementService sellerOrderManagementService;
    @Autowired PurchaseConfirmationService purchaseConfirmationService;
    @Autowired PurchaseConfirmationQuantities purchaseConfirmationQuantities;
    @Autowired ReviewService reviewService;
    @Autowired UserRepository userRepository;
    @Autowired SellerRepository sellerRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductOptionGroupRepository optionGroupRepository;
    @Autowired ProductOptionValueRepository optionValueRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired ProductVariantOptionValueRepository variantOptionValueRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired SellerOrderRepository sellerOrderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired ShipmentRepository shipmentRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired EntityManager entityManager;
    @Autowired com.giftmarket.product.service.ProductVariantService variantService;

    @Autowired com.giftmarket.product.service.ProductOptionService optionService;
    @Autowired com.giftmarket.payment.service.PaymentTransactionService paymentTransactions;

    @MockitoBean PaymentGatewayRegistry gatewayRegistry;

    @Test
    void completesOrderPaymentDeliveryPurchaseConfirmationAndReview() {
        Fixture fixture = persistProductFixture();
        PaymentGateway gateway = mock(PaymentGateway.class);
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);

        OrderCreateResponse prepared = orderService.createDirectOrder(
                fixture.buyer().getId(),
                new DirectOrderCreateRequest(
                        UUID.randomUUID().toString(), fixture.product().getId(),
                        fixture.variant().getId(), ORDER_QUANTITY,
                        "구매자", "010-1234-5678", "12345", "서울시 중구", "101호"
                )
        );
        entityManager.flush();
        entityManager.clear();

        Order pendingOrder = orderRepository.findById(prepared.orderId()).orElseThrow();
        Payment readyPayment = paymentRepository.findById(prepared.paymentId()).orElseThrow();
        SellerOrder pendingSellerOrder = sellerOrderRepository
                .findAllByOrderIdOrderByIdAsc(prepared.orderId()).getFirst();
        OrderItem pendingItem = orderItemRepository
                .findAllByOrderIdOrderByIdAsc(prepared.orderId()).getFirst();

        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(readyPayment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(pendingSellerOrder.getStatus()).isEqualTo(SellerOrderStatus.PENDING_PAYMENT);
        assertOrderItemSnapshot(pendingItem, fixture);
        assertThat(variantRepository.findById(fixture.variant().getId()).orElseThrow().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_QUANTITY);
        assertThat(productRepository.findById(fixture.product().getId()).orElseThrow().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_QUANTITY);

        LocalDateTime approvedAt = LocalDateTime.now();
        given(gateway.confirm(any())).willReturn(new GatewayConfirmResult(
                GatewayPaymentStatus.PAID, "test-payment-key", "test-transaction-key",
                prepared.merchantPaymentId(), prepared.totalAmount(), "KRW",
                PaymentMethod.CARD, null, "DONE", approvedAt
        ));
        paymentService.confirm(
                fixture.buyer().getId(), prepared.paymentId(),
                new PaymentConfirmRequest(
                        "test-payment-key", prepared.merchantPaymentId(), prepared.totalAmount()
                )
        );
        ArgumentCaptor<GatewayConfirmCommand> gatewayCommand =
                ArgumentCaptor.forClass(GatewayConfirmCommand.class);
        verify(gateway).confirm(gatewayCommand.capture());
        assertThat(gatewayCommand.getValue().merchantPaymentId())
                .isEqualTo(prepared.merchantPaymentId());
        assertThat(gatewayCommand.getValue().amount()).isEqualTo(prepared.totalAmount());

        ProductVariant renamedVariant = variantRepository.findById(fixture.variant().getId()).orElseThrow();
        String systemSku = renamedVariant.getSkuCode();
        int stockAfterPayment = renamedVariant.getStockQuantity();
        List<Long> optionValueIds = variantOptionValueRepository.findAllByVariantIdIn(List.of(renamedVariant.getId()))
                .stream().map(mapping -> mapping.getOptionValue().getId()).toList();
        variantService.updateProductVariants(fixture.sellerOwner().getId(), fixture.product().getId(),
                new com.giftmarket.product.dto.request.ProductVariantUpdateRequest(List.of(
                        new com.giftmarket.product.dto.request.ProductVariantRequest(renamedVariant.getId(),
                                "CHANGED-F1", optionValueIds, renamedVariant.getAdditionalPrice(), null, true))));
        renamedVariant.deactivate();
        entityManager.flush();
        entityManager.clear();
        OrderItem retainedItem = orderItemRepository.findById(pendingItem.getId()).orElseThrow();
        assertOrderItemSnapshot(retainedItem, fixture);
        assertThat(retainedItem.getVariant().getSkuCode()).isEqualTo(systemSku);
        assertThat(retainedItem.getVariant().getStockQuantity()).isEqualTo(stockAfterPayment);
        retainedItem.getVariant().activate();

        sellerOrderManagementService.prepare(
                fixture.sellerOwner().getId(), pendingSellerOrder.getId()
        );
        sellerOrderManagementService.ship(
                fixture.sellerOwner().getId(), pendingSellerOrder.getId(),
                new SellerOrderShipRequest("테스트택배", "TRACK-123")
        );
        assertThat(orderItemRepository.findById(pendingItem.getId()).orElseThrow().getConfirmedQuantity())
                .isZero();
        assertThatThrownBy(() -> purchaseConfirmationService.confirm(
                fixture.buyer().getId(), prepared.orderId(), pendingItem.getId()
        )).isInstanceOf(OrderException.class);
        sellerOrderManagementService.deliver(
                fixture.sellerOwner().getId(), pendingSellerOrder.getId()
        );

        OrderItem deliveredItem = orderItemRepository.findById(pendingItem.getId()).orElseThrow();
        assertThat(deliveredItem.getConfirmedQuantity()).isZero();
        purchaseConfirmationService.confirm(
                fixture.buyer().getId(), prepared.orderId(), deliveredItem.getId()
        );
        assertThatThrownBy(() -> purchaseConfirmationService.confirm(
                fixture.buyer().getId(), prepared.orderId(), deliveredItem.getId()
        )).isInstanceOf(OrderException.class);

        reviewService.create(
                fixture.buyer().getId(),
                new ReviewUpsertRequest(deliveredItem.getId(), 5, "배송과 상품 모두 만족합니다.", List.of())
        );

        entityManager.flush();
        entityManager.clear();

        Order order = orderRepository.findById(prepared.orderId()).orElseThrow();
        Payment payment = paymentRepository.findById(prepared.paymentId()).orElseThrow();
        SellerOrder sellerOrder = sellerOrderRepository.findById(pendingSellerOrder.getId()).orElseThrow();
        OrderItem orderItem = orderItemRepository.findById(deliveredItem.getId()).orElseThrow();
        List<Shipment> shipments = shipmentRepository.findAll();
        List<Review> reviews = reviewRepository.findAll();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getTotalProductAmount()).isEqualTo((PRODUCT_PRICE + ADDITIONAL_PRICE) * ORDER_QUANTITY);
        assertThat(order.getTotalShippingFee()).isEqualTo(SHIPPING_FEE);
        assertThat(order.getTotalAmount()).isEqualTo(prepared.totalAmount());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmount()).isEqualTo(order.getTotalAmount());
        assertThat(payment.getApprovedAt()).isNotNull();
        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.DELIVERED);
        assertThat(orderItem.getQuantity()).isEqualTo(ORDER_QUANTITY);
        assertThat(orderItem.getCanceledQuantity()).isZero();
        assertThat(orderItem.getReturnedQuantity()).isZero();
        assertThat(orderItem.getExchangedQuantity()).isZero();
        assertThat(orderItem.getConfirmedQuantity()).isEqualTo(ORDER_QUANTITY);
        assertThat(purchaseConfirmationQuantities.confirmable(
                orderItem, purchaseConfirmationQuantities.load(List.of(orderItem.getId()))
        )).isZero();
        assertOrderItemSnapshot(orderItem, fixture);

        assertThat(shipments).hasSize(1);
        Shipment shipment = shipments.getFirst();
        assertThat(shipment.getSellerOrder().getId()).isEqualTo(sellerOrder.getId());
        assertThat(shipment.getType()).isEqualTo(ShipmentType.ORIGINAL_OUTBOUND);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(shipment.getShippingCompany()).isEqualTo("테스트택배");
        assertThat(shipment.getTrackingNumber()).isEqualTo("TRACK-123");
        assertThat(shipment.getShippedAt()).isNotNull();
        assertThat(shipment.getDeliveredAt()).isNotNull();

        assertThat(reviews).hasSize(1);
        Review review = reviews.getFirst();
        assertThat(review.getUser().getId()).isEqualTo(fixture.buyer().getId());
        assertThat(review.getOrderItem().getId()).isEqualTo(orderItem.getId());
        assertThat(review.getProduct().getId()).isEqualTo(fixture.product().getId());
        assertThat(review.getVariant().getId()).isEqualTo(fixture.variant().getId());
        assertThat(review.getProductNameSnapshot()).isEqualTo("통합 테스트 상품");
        assertThat(review.getOptionSnapshot()).isEqualTo("색상: 빨강");
        assertThat(review.getUnitPriceSnapshot()).isEqualTo(PRODUCT_PRICE + ADDITIONAL_PRICE);
        assertThat(review.getDeletedAt()).isNull();

        assertThat(variantRepository.findById(fixture.variant().getId()).orElseThrow().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_QUANTITY);
        assertThat(productRepository.findById(fixture.product().getId()).orElseThrow().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_QUANTITY);
    }

    @Test
    void archivedOptionsCannotBecomePlainOrdersAndRestoreKeepsOriginalStock() {
        Fixture f = persistProductFixture();
        var saved = savedOptions(f);
        optionService.updateProductOptions(f.sellerOwner().getId(), f.product().getId(),
                new com.giftmarket.product.dto.request.ProductOptionUpdateRequest(List.of()));
        entityManager.flush();
        assertThat(f.variant().getStockQuantity()).isEqualTo(10);
        assertThat(f.product().getStockQuantity()).isZero();
        assertThatThrownBy(() -> orderService.createDirectOrder(f.buyer().getId(),
                new DirectOrderCreateRequest(UUID.randomUUID().toString(), f.product().getId(), null, 1,
                        "구매자", "010-1234-5678", "12345", "주소", "상세")))
                .isInstanceOf(RuntimeException.class);
        assertThat(orderRepository.findAll()).isEmpty();
        restoreOptions(f, saved);
        assertThat(f.variant().getStockQuantity()).isEqualTo(10);
        assertThat(f.product().getStockQuantity()).isEqualTo(10);
        var ordered = orderOne(f);
        entityManager.flush();
        assertThat(f.variant().getStockQuantity()).isEqualTo(9);
        assertThat(orderItemRepository.findAllByOrderIdOrderByIdAsc(ordered.orderId()).getFirst().getVariant().getId())
                .isEqualTo(f.variant().getId());
    }

    @Test
    void archiveAfterPreparationBlocksGatewayApprovalWithoutLosingReservation() {
        Fixture f = persistProductFixture();
        var ordered = orderOne(f);
        optionService.updateProductOptions(f.sellerOwner().getId(), f.product().getId(),
                new com.giftmarket.product.dto.request.ProductOptionUpdateRequest(List.of()));
        entityManager.flush();
        assertThatThrownBy(() -> paymentService.confirm(f.buyer().getId(), ordered.paymentId(),
                new PaymentConfirmRequest("not-sent-to-pg", ordered.merchantPaymentId(), ordered.totalAmount())))
                .isInstanceOf(com.giftmarket.product.exception.ProductConflictException.class);
        org.mockito.Mockito.verifyNoInteractions(gatewayRegistry);
        assertThat(f.variant().getStockQuantity()).isEqualTo(9);
        assertThat(f.product().getStockQuantity()).isZero();
        assertThat(paymentRepository.findById(ordered.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void paymentFailureWhileArchivedRestoresOriginalVariantExactlyOnce() {
        Fixture f = persistProductFixture();
        var saved = savedOptions(f);
        var ordered = orderOne(f);
        paymentTransactions.startConfirm(f.buyer().getId(), ordered.paymentId(),
                new PaymentConfirmRequest("failed-key", ordered.merchantPaymentId(), ordered.totalAmount()));
        optionService.updateProductOptions(f.sellerOwner().getId(), f.product().getId(),
                new com.giftmarket.product.dto.request.ProductOptionUpdateRequest(List.of()));
        paymentTransactions.fail(f.buyer().getId(), ordered.paymentId(), "DECLINED", "테스트 실패", "ABORTED");
        paymentTransactions.fail(f.buyer().getId(), ordered.paymentId(), "DECLINED", "중복 결과", "ABORTED");
        entityManager.flush();
        assertThat(f.variant().getStockQuantity()).isEqualTo(10);
        assertThat(f.product().getStockQuantity()).isZero();
        restoreOptions(f, saved);
        assertThat(f.product().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void partialMappingLossWithOrderHistoryBlocksWritesAndPreservesOrderSnapshot() {
        Fixture f = persistProductFixture();
        ProductOptionGroup extra = optionGroupRepository.save(ProductOptionGroup.create(f.product(), "추가", 2));
        ProductOptionValue value = optionValueRepository.save(ProductOptionValue.create(extra, "값", 0));
        var extraMapping = variantOptionValueRepository.save(ProductVariantOptionValue.create(f.variant(), value));
        f.variant().update(f.variant().getSellerSkuCode(), f.variant().getCombinationKey() + "|" + extra.getId() + ":" + value.getId(),
                f.variant().getAdditionalPrice());
        entityManager.flush();
        var ordered = orderOne(f);
        OrderItem item = orderItemRepository.findAllByOrderIdOrderByIdAsc(ordered.orderId()).getFirst();
        String snapshot = item.getOptionSnapshot();
        variantOptionValueRepository.delete(extraMapping);
        entityManager.flush();
        assertThat(variantService.getProductVariants(f.sellerOwner().getId(), f.product().getId()).getVariants().getFirst().isMappingValid()).isFalse();
        assertThatThrownBy(() -> optionService.updateProductOptions(f.sellerOwner().getId(), f.product().getId(),
                new com.giftmarket.product.dto.request.ProductOptionUpdateRequest(List.of())))
                .isInstanceOf(com.giftmarket.product.exception.ProductConflictException.class).hasMessageContaining("매핑");
        assertThat(item.getVariant().getId()).isEqualTo(f.variant().getId());
        assertThat(item.getOptionSnapshot()).isEqualTo(snapshot);
        assertThat(f.variant().getStockQuantity()).isEqualTo(9);
    }

    @Test
    void legacyMixedOwnersAreQuarantinedAndFailedPaymentRestoresOnlyItsOriginalProduct() {
        Fixture f = persistProductFixture();
        Product plain = productRepository.save(Product.createDraft(f.product().getSeller(), f.product().getCategory(),
                "혼재 데이터", null, null, "설명", 10_000L, 10, null, true, 0L, 1, 3_000L, 6_000L));
        plain.startSale();
        entityManager.flush();
        var ordered = orderService.createDirectOrder(f.buyer().getId(), new DirectOrderCreateRequest(UUID.randomUUID().toString(),
                plain.getId(), null, 1, "구매자", "010-1234-5678", "12345", "주소", null));
        paymentTransactions.startConfirm(f.buyer().getId(), ordered.paymentId(),
                new PaymentConfirmRequest("legacy-key", ordered.merchantPaymentId(), ordered.totalAmount()));
        // Isolated fixture reproduces data created by the old unsafe type conversion.
        var group = optionGroupRepository.save(ProductOptionGroup.create(plain, "이전 옵션", 0));
        var value = optionValueRepository.save(ProductOptionValue.create(group, "값", 0));
        var legacy = variantRepository.save(ProductVariant.create(plain, "LEGACY", group.getId() + ":" + value.getId(), 0L, 10));
        variantOptionValueRepository.save(ProductVariantOptionValue.create(legacy, value));
        entityManager.flush();
        assertThatThrownBy(() -> optionService.updateProductOptions(f.sellerOwner().getId(), plain.getId(),
                new com.giftmarket.product.dto.request.ProductOptionUpdateRequest(List.of())))
                .isInstanceOf(com.giftmarket.product.exception.ProductConflictException.class).hasMessageContaining("혼재");
        assertThat(plain.getStockQuantity()).isEqualTo(9);
        assertThat(legacy.getStockQuantity()).isEqualTo(10);
        paymentTransactions.fail(f.buyer().getId(), ordered.paymentId(), "FAILED", "실패", "ABORTED");
        paymentTransactions.fail(f.buyer().getId(), ordered.paymentId(), "FAILED", "중복 결과", "ABORTED");
        entityManager.flush();
        assertThat(plain.getStockQuantity()).isEqualTo(10);
        assertThat(legacy.getStockQuantity()).isEqualTo(10);
    }

    private OrderCreateResponse orderOne(Fixture f) {
        return orderService.createDirectOrder(f.buyer().getId(), new DirectOrderCreateRequest(
                UUID.randomUUID().toString(), f.product().getId(), f.variant().getId(), 1,
                "구매자", "010-1234-5678", "12345", "주소", "상세"));
    }

    private com.giftmarket.product.dto.request.ProductOptionUpdateRequest savedOptions(Fixture f) {
        return new com.giftmarket.product.dto.request.ProductOptionUpdateRequest(
                optionService.getProductOptions(f.sellerOwner().getId(), f.product().getId()).getOptionGroups().stream()
                        .map(g -> new com.giftmarket.product.dto.request.ProductOptionGroupRequest(g.getId(), g.getName(), g.getSortOrder(),
                                g.getValues().stream().map(v -> new com.giftmarket.product.dto.request.ProductOptionValueRequest(
                                        v.getId(), v.getValue(), v.getSortOrder())).toList())).toList());
    }

    private void restoreOptions(Fixture f, com.giftmarket.product.dto.request.ProductOptionUpdateRequest options) {
        optionService.updateProductOptions(f.sellerOwner().getId(), f.product().getId(), options);
        var ids = variantOptionValueRepository.findAllByVariantIdIn(List.of(f.variant().getId())).stream()
                .map(m -> m.getOptionValue().getId()).toList();
        variantService.updateProductVariants(f.sellerOwner().getId(), f.product().getId(),
                new com.giftmarket.product.dto.request.ProductVariantUpdateRequest(List.of(
                        new com.giftmarket.product.dto.request.ProductVariantRequest(f.variant().getId(),
                                f.variant().getSellerSkuCode(), ids, f.variant().getAdditionalPrice(), null, true))));
        entityManager.flush();
    }

    private Fixture persistProductFixture() {
        User buyer = userRepository.save(User.createOAuthUser(
                "buyer@example.test", "구매자", null, AuthProvider.GOOGLE, "buyer-provider-id"
        ));
        User sellerOwner = userRepository.save(User.createOAuthUser(
                "seller@example.test", "판매자", null, AuthProvider.GOOGLE, "seller-provider-id"
        ));
        Seller seller = sellerRepository.save(Seller.create(sellerOwner, "테스트 상점", "통합 테스트 상점"));
        Category category = categoryRepository.save(Category.create(null, "통합 테스트 카테고리", 1));
        Product product = productRepository.save(Product.createDraft(
                seller, category, "통합 테스트 상품", "테스트 브랜드", "요약", "설명",
                PRODUCT_PRICE, INITIAL_STOCK, "products/test/image.jpg", false,
                SHIPPING_FEE, 1, 3_000L, 6_000L
        ));
        ProductOptionGroup group = optionGroupRepository.save(ProductOptionGroup.create(product, "색상", 1));
        ProductOptionValue value = optionValueRepository.save(ProductOptionValue.create(group, "빨강", 1));
        ProductVariant variant = variantRepository.save(ProductVariant.create(
                product, "INTEGRATION-SKU", group.getId() + ":" + value.getId(), ADDITIONAL_PRICE, INITIAL_STOCK
        ));
        variantOptionValueRepository.save(ProductVariantOptionValue.create(variant, value));
        product.startSale();
        entityManager.flush();
        return new Fixture(buyer, sellerOwner, product, variant);
    }

    private void assertOrderItemSnapshot(OrderItem item, Fixture fixture) {
        assertThat(item.getProduct().getId()).isEqualTo(fixture.product().getId());
        assertThat(item.getVariant().getId()).isEqualTo(fixture.variant().getId());
        assertThat(item.getProductName()).isEqualTo("통합 테스트 상품");
        assertThat(item.getOptionSnapshot()).isEqualTo("색상: 빨강");
        assertThat(item.getUnitPrice()).isEqualTo(PRODUCT_PRICE + ADDITIONAL_PRICE);
        assertThat(item.getQuantity()).isEqualTo(ORDER_QUANTITY);
        assertThat(item.getTotalPrice()).isEqualTo((PRODUCT_PRICE + ADDITIONAL_PRICE) * ORDER_QUANTITY);
        assertThat(item.getShippingFee()).isEqualTo(SHIPPING_FEE);
    }

    private record Fixture(User buyer, User sellerOwner, Product product, ProductVariant variant) {}
}
