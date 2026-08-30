package com.giftmarket.order.integration;

import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.cart.repository.CartItemRepository;
import com.giftmarket.order.dto.request.*;
import com.giftmarket.order.dto.response.OrderCreateResponse;
import com.giftmarket.order.dto.response.OrderCancellationResponse;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.order.service.*;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.payment.service.OrderCancellationRefundExecutionService;
import com.giftmarket.payment.service.PaymentService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
class MultiSellerOrderClaimIntegrationTest {

    private static final int INITIAL_STOCK = 10;
    private static final int QUANTITY = 2;
    private static final long A_UNIT_PRICE = 11_000L;
    private static final long A_SHIPPING_FEE = 3_000L;
    private static final long A_TOTAL = A_UNIT_PRICE * QUANTITY + A_SHIPPING_FEE;
    private static final long B_UNIT_PRICE = 22_000L;
    private static final long B_SHIPPING_FEE = 4_000L;
    private static final long B_TOTAL = B_UNIT_PRICE * QUANTITY + B_SHIPPING_FEE;

    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired OrderCancellationWorkflowService cancellationWorkflowService;
    @Autowired OrderCancellationRefundExecutionService cancellationRefundExecutionService;
    @Autowired SellerOrderManagementService orderManagementService;
    @Autowired ReturnRequestService returnRequestService;
    @Autowired SellerReturnRequestService sellerReturnRequestService;
    @Autowired SellerReturnRequestWorkflowService returnWorkflowService;
    @Autowired UserRepository userRepository;
    @Autowired SellerRepository sellerRepository;
    @Autowired CartItemRepository cartItemRepository;
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
    @Autowired OrderCancellationRepository orderCancellationRepository;
    @Autowired ReturnRequestRepository returnRequestRepository;
    @Autowired ReturnRequestItemRepository returnItemRepository;
    @Autowired PaymentCancellationRepository paymentCancellationRepository;
    @Autowired EntityManager entityManager;

    @MockitoBean PaymentGatewayRegistry gatewayRegistry;

    @Test
    void sellerAFullCancellationDoesNotBlockSellerBDelivery() {
        MultiSellerOrder fixture = createPaidOrder("cancel-deliver");

        OrderCancellationResponse cancellation = cancel(
                fixture, fixture.a(), QUANTITY, new AtomicLong()
        );
        PaymentCancellation completedRefund = paymentCancellationRepository
                .findByOrderCancellationId(cancellation.cancellationId()).orElseThrow();
        String idempotencyKey = completedRefund.getIdempotencyKey();
        cancellationRefundExecutionService.execute(cancellation.cancellationId());
        orderManagementService.prepare(fixture.b().ownerId(), fixture.b().sellerOrderId());
        orderManagementService.ship(
                fixture.b().ownerId(), fixture.b().sellerOrderId(),
                new SellerOrderShipRequest("B택배", "B-TRACK")
        );
        orderManagementService.deliver(fixture.b().ownerId(), fixture.b().sellerOrderId());

        entityManager.flush();
        entityManager.clear();

        Order order = orderRepository.findById(fixture.orderId()).orElseThrow();
        Payment payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        SellerOrder sellerOrderA = sellerOrderRepository.findById(fixture.a().sellerOrderId()).orElseThrow();
        SellerOrder sellerOrderB = sellerOrderRepository.findById(fixture.b().sellerOrderId()).orElseThrow();
        OrderItem itemA = orderItemRepository.findById(fixture.a().orderItemId()).orElseThrow();
        OrderItem itemB = orderItemRepository.findById(fixture.b().orderItemId()).orElseThrow();
        PaymentCancellation refund = paymentCancellationRepository
                .findByOrderCancellationId(cancellation.cancellationId()).orElseThrow();

        assertThat(sellerOrderA.getStatus()).isEqualTo(SellerOrderStatus.CANCELLED);
        assertThat(itemA.getCanceledQuantity()).isEqualTo(QUANTITY);
        assertThat(refund.getStatus()).isEqualTo(PaymentCancellationStatus.SUCCEEDED);
        assertThat(refund.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(refund.getAmount()).isEqualTo(A_TOTAL);
        assertThat(refund.getAmount()).isLessThan(B_TOTAL);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_CANCELED);
        assertThat(payment.getAmount() - refund.getAmount()).isEqualTo(B_TOTAL);

        assertThat(sellerOrderB.getStatus()).isEqualTo(SellerOrderStatus.DELIVERED);
        assertUntouchedQuantities(itemB);
        Shipment shipment = shipmentRepository.findBySellerOrderIdAndType(
                sellerOrderB.getId(), ShipmentType.ORIGINAL_OUTBOUND
        ).orElseThrow();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(shipment.getShippingCompany()).isEqualTo("B택배");
        assertThat(shipmentRepository.findBySellerOrderIdAndType(
                sellerOrderA.getId(), ShipmentType.ORIGINAL_OUTBOUND
        )).isEmpty();
        assertStock(fixture.a(), INITIAL_STOCK);
        assertStock(fixture.b(), INITIAL_STOCK - QUANTITY);
        assertThat(paymentCancellationRepository.findAll()).hasSize(1);
        assertThat(paymentCancellationRepository.sumAmountByPaymentIdAndStatus(
                payment.getId(), PaymentCancellationStatus.SUCCEEDED
        )).isEqualTo(A_TOTAL);
        verify(fixture.gateway()).cancel(any(GatewayCancelCommand.class));
    }

    @Test
    void duplicatePaymentCancellationForSameOrderCancellationIsRejectedByDatabase() {
        MultiSellerOrder fixture = createPaidOrder("duplicate-refund-row");
        OrderCancellationResponse response = cancel(
                fixture, fixture.a(), QUANTITY, new AtomicLong()
        );
        entityManager.flush();

        Payment payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        OrderCancellation cancellation = orderCancellationRepository
                .findById(response.cancellationId()).orElseThrow();
        PaymentCancellation existing = paymentCancellationRepository
                .findByOrderCancellationId(cancellation.getId()).orElseThrow();
        PaymentCancellation duplicate = PaymentCancellation.createPartial(
                payment,
                cancellation,
                "duplicate-client-" + UUID.randomUUID(),
                "duplicate-idempotency-" + UUID.randomUUID(),
                existing.getAmount(),
                "duplicate constraint check",
                LocalDateTime.now()
        );

        assertThat(paymentCancellationRepository.findAll()).hasSize(1);
        assertThatThrownBy(() -> paymentCancellationRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sellerAReturnDoesNotMutateDeliveredSellerB() {
        MultiSellerOrder fixture = createPaidOrder("return-isolation");
        deliver(fixture.a(), "A");
        deliver(fixture.b(), "B");
        long sellerBShipmentCountBefore = shipmentRepository.count();
        int sellerBStockBefore = variantRepository.findById(fixture.b().variantId()).orElseThrow().getStockQuantity();
        configureReturnGateway(fixture);

        ReturnRequestResponse created = returnRequestService.create(
                fixture.buyerId(), fixture.orderId(), fixture.a().sellerOrderId(),
                new ReturnRequestCreateRequest(
                        UUID.randomUUID().toString(), ReturnReasonType.DEFECTIVE, "불량",
                        "회수인", "010-1111-2222", "12345", "서울", null,
                        List.of(new ReturnRequestItemRequest(fixture.a().orderItemId(), 1)), List.of()
                )
        );
        sellerReturnRequestService.approve(fixture.a().ownerId(), created.returnRequestId(), null);
        sellerReturnRequestService.collect(
                fixture.a().ownerId(), created.returnRequestId(), "회수택배", "RETURN-A"
        );
        sellerReturnRequestService.receive(fixture.a().ownerId(), created.returnRequestId());
        returnWorkflowService.inspect(
                fixture.a().ownerId(), created.returnRequestId(),
                new SellerReturnInspectRequest(List.of(
                        new SellerReturnInspectionItemRequest(
                                fixture.a().orderItemId(), ReturnInspectionResult.RESTOCKABLE
                        )
                ))
        );

        entityManager.flush();
        entityManager.clear();

        ReturnRequest request = returnRequestRepository.findById(created.returnRequestId()).orElseThrow();
        ReturnRequestItem returnItem = returnItemRepository
                .findAllByReturnRequestIdOrderByIdAsc(request.getId()).getFirst();
        PaymentCancellation refund = paymentCancellationRepository
                .findByReturnRequestId(request.getId()).orElseThrow();
        SellerOrder sellerOrderB = sellerOrderRepository.findById(fixture.b().sellerOrderId()).orElseThrow();
        OrderItem itemB = orderItemRepository.findById(fixture.b().orderItemId()).orElseThrow();

        assertThat(request.getStatus()).isEqualTo(ReturnRequestStatus.COMPLETED);
        assertThat(request.getResponsibility()).isEqualTo(ReturnResponsibility.SELLER);
        assertThat(returnItem.getQuantity()).isEqualTo(1);
        assertThat(returnItem.getRestockedQuantity()).isEqualTo(1);
        assertThat(orderItemRepository.findById(fixture.a().orderItemId()).orElseThrow().getReturnedQuantity())
                .isEqualTo(1);
        assertThat(refund.getStatus()).isEqualTo(PaymentCancellationStatus.SUCCEEDED);
        assertThat(refund.getAmount()).isEqualTo(A_UNIT_PRICE);
        assertThat(paymentRepository.findById(fixture.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PARTIALLY_CANCELED);

        assertThat(sellerOrderB.getStatus()).isEqualTo(SellerOrderStatus.DELIVERED);
        assertUntouchedQuantities(itemB);
        assertThat(shipmentRepository.count()).isEqualTo(sellerBShipmentCountBefore + 1);
        assertThat(shipmentRepository.findBySellerOrderIdAndType(
                sellerOrderB.getId(), ShipmentType.ORIGINAL_OUTBOUND
        )).hasValueSatisfying(value -> assertThat(value.getStatus()).isEqualTo(ShipmentStatus.DELIVERED));
        assertThat(variantRepository.findById(fixture.b().variantId()).orElseThrow().getStockQuantity())
                .isEqualTo(sellerBStockBefore);
        assertStock(fixture.a(), INITIAL_STOCK - QUANTITY + 1);
        assertStock(fixture.b(), INITIAL_STOCK - QUANTITY);
    }

    @Test
    void secondSellerCanCancelAgainstRemainingPaymentBalance() {
        MultiSellerOrder fixture = createPaidOrder("sequential-cancel");
        AtomicLong refunded = new AtomicLong();

        cancel(fixture, fixture.a(), QUANTITY, refunded);
        cancel(fixture, fixture.b(), QUANTITY, refunded);

        entityManager.flush();
        entityManager.clear();

        Order order = orderRepository.findById(fixture.orderId()).orElseThrow();
        Payment payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        List<PaymentCancellation> refunds = paymentCancellationRepository.findAll();
        long succeededAmount = refunds.stream()
                .filter(value -> value.getStatus() == PaymentCancellationStatus.SUCCEEDED)
                .mapToLong(PaymentCancellation::getAmount).sum();

        assertThat(refunds).hasSize(2);
        assertThat(refunds).extracting(PaymentCancellation::getAmount)
                .containsExactlyInAnyOrder(A_TOTAL, B_TOTAL);
        assertThat(succeededAmount).isEqualTo(payment.getAmount());
        assertThat(succeededAmount).isLessThanOrEqualTo(payment.getAmount());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(sellerOrderRepository.findAllByOrderIdOrderByIdAsc(order.getId()))
                .allMatch(value -> value.getStatus() == SellerOrderStatus.CANCELLED);
        assertThat(orderItemRepository.findAllByOrderIdOrderByIdAsc(order.getId()))
                .allMatch(OrderItem::isFullyCanceled);
        assertStock(fixture.a(), INITIAL_STOCK);
        assertStock(fixture.b(), INITIAL_STOCK);
    }

    private OrderCancellationResponse cancel(
            MultiSellerOrder fixture,
            Side side,
            int quantity,
            AtomicLong refunded
    ) {
        configureCancellationGateway(fixture, refunded);
        return cancellationWorkflowService.create(
                fixture.buyerId(), fixture.orderId(),
                new OrderCancellationCreateRequest(
                        UUID.randomUUID().toString(), side.sellerOrderId(), "취소",
                        List.of(new OrderCancellationItemRequest(side.orderItemId(), quantity))
                )
        );
    }

    private void configureCancellationGateway(MultiSellerOrder fixture, AtomicLong refunded) {
        Payment payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        given(fixture.gateway().getPayment(payment.getProviderPaymentKey())).willAnswer(invocation ->
                new GatewayPaymentQueryResult(
                        refunded.get() == 0L ? GatewayPaymentStatus.PAID : GatewayPaymentStatus.PARTIALLY_CANCELED,
                        payment.getProviderPaymentKey(), "payment-transaction",
                        payment.getMerchantPaymentId(), payment.getAmount(), "KRW",
                        PaymentMethod.CARD, null, "DONE", payment.getApprovedAt(),
                        payment.getAmount() - refunded.get(), null, true
                )
        );
        given(fixture.gateway().cancel(any(GatewayCancelCommand.class))).willAnswer(invocation -> {
            GatewayCancelCommand command = invocation.getArgument(0);
            long totalRefunded = refunded.addAndGet(command.cancelAmount());
            long remaining = command.amount() - totalRefunded;
            return new GatewayCancelResult(
                    remaining == 0L ? GatewayPaymentStatus.CANCELED : GatewayPaymentStatus.PARTIALLY_CANCELED,
                    command.providerPaymentKey(), "cancel-transaction-" + totalRefunded,
                    command.merchantPaymentId(), command.amount(), remaining,
                    command.currency(), remaining == 0L ? "CANCELED" : "PARTIAL_CANCELED",
                    LocalDateTime.now(), command.cancelAmount(), "DONE", remaining
            );
        });
    }

    private void configureReturnGateway(MultiSellerOrder fixture) {
        Payment payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        given(fixture.gateway().getPayment(payment.getProviderPaymentKey())).willReturn(
                new GatewayPaymentQueryResult(
                        GatewayPaymentStatus.PAID, payment.getProviderPaymentKey(),
                        "payment-transaction", payment.getMerchantPaymentId(),
                        payment.getAmount(), payment.getCurrency(), PaymentMethod.CARD,
                        null, "DONE", payment.getApprovedAt(), payment.getAmount(), null, true
                )
        );
        given(fixture.gateway().cancel(any(GatewayCancelCommand.class))).willAnswer(invocation -> {
            GatewayCancelCommand command = invocation.getArgument(0);
            long remaining = command.amount() - command.cancelAmount();
            return new GatewayCancelResult(
                    GatewayPaymentStatus.PARTIALLY_CANCELED,
                    command.providerPaymentKey(), "return-cancel-transaction",
                    command.merchantPaymentId(), command.amount(), remaining,
                    command.currency(), "PARTIAL_CANCELED", LocalDateTime.now(),
                    command.cancelAmount(), "DONE", remaining
            );
        });
    }

    private MultiSellerOrder createPaidOrder(String suffix) {
        User buyer = userRepository.save(User.createOAuthUser(
                "multi-buyer-" + suffix + "@example.test", "구매자", null,
                AuthProvider.GOOGLE, "multi-buyer-" + suffix
        ));
        SideDraft a = createSide(suffix + "-a", buyer, 10_000L, 1_000L, A_SHIPPING_FEE);
        SideDraft b = createSide(suffix + "-b", buyer, 20_000L, 2_000L, B_SHIPPING_FEE);
        CartItem cartA = cartItemRepository.save(CartItem.create(buyer, a.product(), a.variant(), QUANTITY));
        CartItem cartB = cartItemRepository.save(CartItem.create(buyer, b.product(), b.variant(), QUANTITY));
        entityManager.flush();

        OrderCreateResponse prepared = orderService.createOrder(
                buyer.getId(),
                new OrderCreateRequest(
                        UUID.randomUUID().toString(), List.of(cartA.getId(), cartB.getId()),
                        "구매자", "010-1234-5678", "12345", "서울", null
                )
        );
        PaymentGateway gateway = mock(PaymentGateway.class);
        given(gatewayRegistry.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.confirm(any(GatewayConfirmCommand.class))).willReturn(new GatewayConfirmResult(
                GatewayPaymentStatus.PAID, "multi-payment-key-" + suffix,
                "multi-transaction-" + suffix, prepared.merchantPaymentId(),
                prepared.totalAmount(), "KRW", PaymentMethod.CARD, null,
                "DONE", LocalDateTime.now()
        ));
        paymentService.confirm(
                buyer.getId(), prepared.paymentId(),
                new PaymentConfirmRequest(
                        "multi-payment-key-" + suffix, prepared.merchantPaymentId(), prepared.totalAmount()
                )
        );

        List<SellerOrder> sellerOrders = sellerOrderRepository.findAllByOrderIdOrderByIdAsc(prepared.orderId());
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdOrderByIdAsc(prepared.orderId());
        assertThat(sellerOrders).hasSize(2);
        assertThat(orderItems).hasSize(2);
        Side sideA = resolved(a, sellerOrders, orderItems);
        Side sideB = resolved(b, sellerOrders, orderItems);
        assertThat(sideA.sellerOrderId()).isNotEqualTo(sideB.sellerOrderId());
        assertThat(paymentRepository.findAllByOrderIdOrderByIdAsc(prepared.orderId())).hasSize(1);
        assertThat(prepared.totalAmount()).isEqualTo(A_TOTAL + B_TOTAL);
        return new MultiSellerOrder(
                buyer.getId(), prepared.orderId(), prepared.paymentId(), sideA, sideB, gateway
        );
    }

    private SideDraft createSide(
            String suffix,
            User buyer,
            long price,
            long additionalPrice,
            long shippingFee
    ) {
        User owner = userRepository.save(User.createOAuthUser(
                "owner-" + suffix + "@example.test", "판매자", null,
                AuthProvider.GOOGLE, "owner-" + suffix
        ));
        Seller seller = sellerRepository.save(Seller.create(owner, "상점 " + suffix, "소개"));
        Category category = categoryRepository.save(Category.create(null, "카테고리 " + suffix, 1));
        Product product = productRepository.save(Product.createDraft(
                seller, category, "상품 " + suffix, "브랜드", "요약", "설명",
                price, INITIAL_STOCK, null, false, shippingFee, 1, 3_000L, 6_000L
        ));
        ProductOptionGroup group = optionGroupRepository.save(ProductOptionGroup.create(product, "색상", 1));
        ProductOptionValue value = optionValueRepository.save(ProductOptionValue.create(group, "빨강", 1));
        ProductVariant variant = variantRepository.save(ProductVariant.create(
                product, "MULTI-SKU-" + suffix, "red-" + suffix,
                additionalPrice, INITIAL_STOCK
        ));
        variantOptionValueRepository.save(ProductVariantOptionValue.create(variant, value));
        product.startSale();
        return new SideDraft(owner.getId(), seller.getId(), product, variant);
    }

    private Side resolved(
            SideDraft draft,
            List<SellerOrder> sellerOrders,
            List<OrderItem> orderItems
    ) {
        SellerOrder sellerOrder = sellerOrders.stream()
                .filter(value -> value.getSeller().getId().equals(draft.sellerId()))
                .findFirst().orElseThrow();
        OrderItem orderItem = orderItems.stream()
                .filter(value -> value.getSellerOrder().getId().equals(sellerOrder.getId()))
                .findFirst().orElseThrow();
        return new Side(
                draft.ownerId(), sellerOrder.getId(), orderItem.getId(),
                draft.product().getId(), draft.variant().getId()
        );
    }

    private void deliver(Side side, String prefix) {
        orderManagementService.prepare(side.ownerId(), side.sellerOrderId());
        orderManagementService.ship(
                side.ownerId(), side.sellerOrderId(),
                new SellerOrderShipRequest(prefix + "택배", prefix + "-TRACK")
        );
        orderManagementService.deliver(side.ownerId(), side.sellerOrderId());
    }

    private void assertUntouchedQuantities(OrderItem item) {
        assertThat(item.getCanceledQuantity()).isZero();
        assertThat(item.getReturnedQuantity()).isZero();
        assertThat(item.getExchangedQuantity()).isZero();
        assertThat(item.getConfirmedQuantity()).isZero();
    }

    private void assertStock(Side side, int expected) {
        assertThat(variantRepository.findById(side.variantId()).orElseThrow().getStockQuantity())
                .isEqualTo(expected);
        assertThat(productRepository.findById(side.productId()).orElseThrow().getStockQuantity())
                .isEqualTo(expected);
    }

    private record SideDraft(Long ownerId, Long sellerId, Product product, ProductVariant variant) {}

    private record Side(
            Long ownerId, Long sellerOrderId, Long orderItemId, Long productId, Long variantId
    ) {}

    private record MultiSellerOrder(
            Long buyerId, Long orderId, Long paymentId, Side a, Side b, PaymentGateway gateway
    ) {}
}
