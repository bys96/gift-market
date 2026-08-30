package com.giftmarket.order.integration;

import com.giftmarket.order.dto.request.*;
import com.giftmarket.order.dto.response.OrderCreateResponse;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.order.service.*;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.payment.service.PaymentService;
import com.giftmarket.payment.service.ReturnRefundExecutionService;
import com.giftmarket.product.entity.*;
import com.giftmarket.product.repository.*;
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
        "payment.toss.secret-key=test-only-key"
})
@Transactional
class ReturnWorkflowIntegrationTest {

    private static final int INITIAL_STOCK = 10;
    private static final int ORDER_QUANTITY = 3;
    private static final int RETURN_QUANTITY = 1;
    private static final long PRODUCT_PRICE = 10_000L;
    private static final long ADDITIONAL_PRICE = 1_000L;
    private static final long UNIT_PRICE = PRODUCT_PRICE + ADDITIONAL_PRICE;
    private static final long SHIPPING_FEE = 3_000L;
    private static final long RETURN_SHIPPING_FEE = 3_000L;

    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired SellerOrderManagementService orderManagementService;
    @Autowired ReturnRequestService returnRequestService;
    @Autowired SellerReturnRequestService sellerReturnRequestService;
    @Autowired SellerReturnRequestWorkflowService returnWorkflowService;
    @Autowired ReturnCompletionService returnCompletionService;
    @Autowired ReturnRefundExecutionService returnRefundExecutionService;
    @Autowired PurchaseConfirmationQuantities purchaseConfirmationQuantities;
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
    @Autowired ReturnRequestRepository returnRequestRepository;
    @Autowired ReturnRequestItemRepository returnItemRepository;
    @Autowired PaymentCancellationRepository cancellationRepository;
    @Autowired EntityManager entityManager;

    @MockitoBean PaymentGatewayRegistry gatewayRegistry;

    @Test
    void buyerResponsibleRestockableReturnCompletesWithShippingCharge() {
        CompletedReturn completed = completeReturn(
                "buyer", ReturnReasonType.CHANGE_OF_MIND, ReturnInspectionResult.RESTOCKABLE
        );

        assertCompleted(completed, ReturnResponsibility.BUYER, RETURN_QUANTITY);
        assertThat(completed.request().getProductRefundAmount()).isEqualTo(UNIT_PRICE);
        assertThat(completed.request().getOriginalShippingRefundAmount()).isZero();
        assertThat(completed.request().getReturnShippingCharge()).isEqualTo(RETURN_SHIPPING_FEE);
        assertThat(completed.request().getRefundAmount()).isEqualTo(UNIT_PRICE - RETURN_SHIPPING_FEE);
        assertThat(completed.cancellation().getAmount()).isEqualTo(UNIT_PRICE - RETURN_SHIPPING_FEE);
        assertThat(completed.variant().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_QUANTITY + RETURN_QUANTITY);
        assertThat(completed.product().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_QUANTITY + RETURN_QUANTITY);
    }

    @Test
    void sellerResponsibleRestockableReturnCompletesWithoutShippingCharge() {
        CompletedReturn completed = completeReturn(
                "seller", ReturnReasonType.DEFECTIVE, ReturnInspectionResult.RESTOCKABLE
        );

        assertCompleted(completed, ReturnResponsibility.SELLER, RETURN_QUANTITY);
        assertThat(completed.request().getProductRefundAmount()).isEqualTo(UNIT_PRICE);
        assertThat(completed.request().getOriginalShippingRefundAmount()).isZero();
        assertThat(completed.request().getReturnShippingCharge()).isZero();
        assertThat(completed.request().getRefundAmount()).isEqualTo(UNIT_PRICE);
        assertThat(completed.cancellation().getAmount()).isEqualTo(UNIT_PRICE);
        assertThat(completed.variant().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_QUANTITY + RETURN_QUANTITY);
        assertThat(completed.product().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_QUANTITY + RETURN_QUANTITY);
    }

    @Test
    void nonRestockableReturnCompletesWithoutRestoringInventory() {
        CompletedReturn completed = completeReturn(
                "non-restockable", ReturnReasonType.DEFECTIVE,
                ReturnInspectionResult.NON_RESTOCKABLE
        );

        assertCompleted(completed, ReturnResponsibility.SELLER, 0);
        assertThat(completed.requestItem().getInspectionResult())
                .isEqualTo(ReturnInspectionResult.NON_RESTOCKABLE);
        assertThat(completed.variant().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_QUANTITY);
        assertThat(completed.product().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_QUANTITY);
    }

    private CompletedReturn completeReturn(
            String suffix,
            ReturnReasonType reasonType,
            ReturnInspectionResult inspectionResult
    ) {
        DeliveredOrder delivered = createDeliveredOrder(suffix);
        PaymentGateway gateway = delivered.gateway();
        Payment paymentBeforeReturn = paymentRepository.findById(delivered.paymentId()).orElseThrow();
        long originalAmount = paymentBeforeReturn.getAmount();
        String providerPaymentKey = paymentBeforeReturn.getProviderPaymentKey();
        String merchantPaymentId = paymentBeforeReturn.getMerchantPaymentId();

        given(gateway.getPayment(providerPaymentKey)).willReturn(new GatewayPaymentQueryResult(
                GatewayPaymentStatus.PAID, providerPaymentKey, "payment-transaction-" + suffix,
                merchantPaymentId, originalAmount, "KRW", PaymentMethod.CARD,
                null, "DONE", paymentBeforeReturn.getApprovedAt(), originalAmount, null, true
        ));
        given(gateway.cancel(any(GatewayCancelCommand.class))).willAnswer(invocation -> {
            GatewayCancelCommand command = invocation.getArgument(0);
            long remaining = command.amount() - command.cancelAmount();
            return new GatewayCancelResult(
                    GatewayPaymentStatus.PARTIALLY_CANCELED,
                    command.providerPaymentKey(), "return-cancel-" + suffix,
                    command.merchantPaymentId(), command.amount(), remaining, command.currency(),
                    "PARTIAL_CANCELED", LocalDateTime.now(), command.cancelAmount(),
                    "DONE", remaining
            );
        });

        ReturnRequestResponse created = returnRequestService.create(
                delivered.buyerId(), delivered.orderId(), delivered.sellerOrderId(),
                new ReturnRequestCreateRequest(
                        UUID.randomUUID().toString(), reasonType, "반품 사유",
                        "회수인", "010-1111-2222", "12345", "서울시 중구", "101호",
                        List.of(new ReturnRequestItemRequest(delivered.orderItemId(), RETURN_QUANTITY)),
                        List.of()
                )
        );
        assertStock(delivered, INITIAL_STOCK - ORDER_QUANTITY);
        sellerReturnRequestService.approve(delivered.sellerOwnerId(), created.returnRequestId(), null);
        assertStock(delivered, INITIAL_STOCK - ORDER_QUANTITY);
        sellerReturnRequestService.collect(
                delivered.sellerOwnerId(), created.returnRequestId(), "회수택배", "RETURN-TRACK-" + suffix
        );
        assertStock(delivered, INITIAL_STOCK - ORDER_QUANTITY);
        sellerReturnRequestService.receive(delivered.sellerOwnerId(), created.returnRequestId());
        assertStock(delivered, INITIAL_STOCK - ORDER_QUANTITY);

        returnWorkflowService.inspect(
                delivered.sellerOwnerId(), created.returnRequestId(),
                new SellerReturnInspectRequest(List.of(
                        new SellerReturnInspectionItemRequest(delivered.orderItemId(), inspectionResult)
                ))
        );
        returnCompletionService.complete(created.returnRequestId());
        assertThatThrownBy(() -> returnRefundExecutionService.execute(created.returnRequestId()))
                .isInstanceOf(PaymentException.class);
        verify(gateway).getPayment(providerPaymentKey);
        verify(gateway).cancel(any(GatewayCancelCommand.class));

        entityManager.flush();
        entityManager.clear();

        ReturnRequest request = returnRequestRepository.findById(created.returnRequestId()).orElseThrow();
        ReturnRequestItem requestItem = returnItemRepository
                .findAllByReturnRequestIdOrderByIdAsc(request.getId()).getFirst();
        Order order = orderRepository.findById(delivered.orderId()).orElseThrow();
        Payment payment = paymentRepository.findById(delivered.paymentId()).orElseThrow();
        SellerOrder sellerOrder = sellerOrderRepository.findById(delivered.sellerOrderId()).orElseThrow();
        OrderItem orderItem = orderItemRepository.findById(delivered.orderItemId()).orElseThrow();
        PaymentCancellation cancellation = cancellationRepository
                .findByReturnRequestId(request.getId()).orElseThrow();
        Product product = productRepository.findById(delivered.productId()).orElseThrow();
        ProductVariant variant = variantRepository.findById(delivered.variantId()).orElseThrow();
        List<Shipment> shipments = shipmentRepository.findAllBySellerOrderIdInAndType(
                List.of(sellerOrder.getId()), ShipmentType.RETURN_COLLECTION
        );
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_CANCELED);
        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.DELIVERED);
        assertThat(cancellationRepository.findAll()).hasSize(1);

        return new CompletedReturn(
                request, requestItem, orderItem, payment, cancellation,
                product, variant, shipments
        );
    }

    private DeliveredOrder createDeliveredOrder(String suffix) {
        User buyer = userRepository.save(User.createOAuthUser(
                "buyer-" + suffix + "@example.test", "구매자", null,
                AuthProvider.GOOGLE, "buyer-provider-" + suffix
        ));
        User sellerOwner = userRepository.save(User.createOAuthUser(
                "seller-" + suffix + "@example.test", "판매자", null,
                AuthProvider.GOOGLE, "seller-provider-" + suffix
        ));
        Seller seller = sellerRepository.save(Seller.create(sellerOwner, "테스트 상점 " + suffix, "소개"));
        Category category = categoryRepository.save(Category.create(null, "반품 카테고리 " + suffix, 1));
        Product product = productRepository.save(Product.createDraft(
                seller, category, "반품 테스트 상품", "브랜드", "요약", "설명",
                PRODUCT_PRICE, INITIAL_STOCK, "products/test/image.jpg", false,
                SHIPPING_FEE, 1, RETURN_SHIPPING_FEE, 6_000L
        ));
        ProductOptionGroup group = optionGroupRepository.save(ProductOptionGroup.create(product, "색상", 1));
        ProductOptionValue value = optionValueRepository.save(ProductOptionValue.create(group, "빨강", 1));
        ProductVariant variant = variantRepository.save(ProductVariant.create(
                product, "RETURN-SKU-" + suffix, "color-red-" + suffix,
                ADDITIONAL_PRICE, INITIAL_STOCK
        ));
        variantOptionValueRepository.save(ProductVariantOptionValue.create(variant, value));
        product.startSale();
        entityManager.flush();

        OrderCreateResponse prepared = orderService.createDirectOrder(
                buyer.getId(),
                new DirectOrderCreateRequest(
                        UUID.randomUUID().toString(), product.getId(), variant.getId(), ORDER_QUANTITY,
                        "구매자", "010-1234-5678", "12345", "서울시 중구", "101호"
                )
        );
        PaymentGateway gateway = mock(PaymentGateway.class);
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.confirm(any(GatewayConfirmCommand.class))).willReturn(new GatewayConfirmResult(
                GatewayPaymentStatus.PAID, "payment-key-" + suffix,
                "payment-transaction-" + suffix, prepared.merchantPaymentId(),
                prepared.totalAmount(), "KRW", PaymentMethod.CARD, null,
                "DONE", LocalDateTime.now()
        ));
        paymentService.confirm(
                buyer.getId(), prepared.paymentId(),
                new PaymentConfirmRequest(
                        "payment-key-" + suffix, prepared.merchantPaymentId(), prepared.totalAmount()
                )
        );
        SellerOrder sellerOrder = sellerOrderRepository
                .findAllByOrderIdOrderByIdAsc(prepared.orderId()).getFirst();
        OrderItem orderItem = orderItemRepository
                .findAllByOrderIdOrderByIdAsc(prepared.orderId()).getFirst();
        orderManagementService.prepare(sellerOwner.getId(), sellerOrder.getId());
        orderManagementService.ship(
                sellerOwner.getId(), sellerOrder.getId(),
                new SellerOrderShipRequest("원배송택배", "OUTBOUND-" + suffix)
        );
        orderManagementService.deliver(sellerOwner.getId(), sellerOrder.getId());
        return new DeliveredOrder(
                buyer.getId(), sellerOwner.getId(), prepared.orderId(), prepared.paymentId(),
                sellerOrder.getId(), orderItem.getId(), product.getId(), variant.getId(), gateway
        );
    }

    private void assertStock(DeliveredOrder delivered, int expected) {
        entityManager.flush();
        assertThat(variantRepository.findById(delivered.variantId()).orElseThrow().getStockQuantity())
                .isEqualTo(expected);
        assertThat(productRepository.findById(delivered.productId()).orElseThrow().getStockQuantity())
                .isEqualTo(expected);
    }

    private void assertCompleted(
            CompletedReturn completed,
            ReturnResponsibility responsibility,
            int expectedRestockedQuantity
    ) {
        assertThat(completed.request().getStatus()).isEqualTo(ReturnRequestStatus.COMPLETED);
        assertThat(completed.request().getResponsibility()).isEqualTo(responsibility);
        assertThat(completed.request().getRequestedAt()).isNotNull();
        assertThat(completed.request().getApprovedAt()).isNotNull();
        assertThat(completed.request().getCollectingAt()).isNotNull();
        assertThat(completed.request().getReceivedAt()).isNotNull();
        assertThat(completed.request().getInspectedAt()).isNotNull();
        assertThat(completed.request().getRefundingAt()).isNotNull();
        assertThat(completed.request().getCompletedAt()).isNotNull();
        assertThat(completed.requestItem().getQuantity()).isEqualTo(RETURN_QUANTITY);
        assertThat(completed.requestItem().getRestockedQuantity()).isEqualTo(expectedRestockedQuantity);
        assertThat(completed.orderItem().getReturnedQuantity()).isEqualTo(RETURN_QUANTITY);
        assertThat(completed.orderItem().getCanceledQuantity()).isZero();
        assertThat(completed.orderItem().getConfirmedQuantity()).isZero();
        assertThat(purchaseConfirmationQuantities.confirmable(
                completed.orderItem(),
                purchaseConfirmationQuantities.load(List.of(completed.orderItem().getId()))
        )).isEqualTo(ORDER_QUANTITY - RETURN_QUANTITY);
        assertThat(completed.payment().getStatus()).isEqualTo(PaymentStatus.PARTIALLY_CANCELED);
        assertThat(completed.cancellation().getStatus())
                .isEqualTo(PaymentCancellationStatus.SUCCEEDED);
        assertThat(completed.cancellation().getReturnRequest().getId())
                .isEqualTo(completed.request().getId());
        assertThat(completed.shipments()).hasSize(1);
        Shipment collection = completed.shipments().getFirst();
        assertThat(collection.getType()).isEqualTo(ShipmentType.RETURN_COLLECTION);
        assertThat(collection.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(collection.getShippingCompany()).isEqualTo("회수택배");
        assertThat(collection.getTrackingNumber()).startsWith("RETURN-TRACK-");
        assertThat(collection.getShippedAt()).isNotNull();
        assertThat(collection.getDeliveredAt()).isNotNull();
        assertThat(shipmentRepository.findAll()).hasSize(2);
    }

    private record DeliveredOrder(
            Long buyerId, Long sellerOwnerId, Long orderId, Long paymentId,
            Long sellerOrderId, Long orderItemId, Long productId, Long variantId,
            PaymentGateway gateway
    ) {}

    private record CompletedReturn(
            ReturnRequest request, ReturnRequestItem requestItem, OrderItem orderItem,
            Payment payment, PaymentCancellation cancellation, Product product,
            ProductVariant variant, List<Shipment> shipments
    ) {}
}
