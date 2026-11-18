package com.giftmarket.order.integration;

import com.giftmarket.cart.dto.request.CartItemCreateRequest;
import com.giftmarket.cart.service.CartService;
import com.giftmarket.order.dto.request.*;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.service.OrderService;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.service.*;
import com.giftmarket.product.entity.*;
import com.giftmarket.product.repository.*;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.*;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:multi-option-purchase;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false",
        "app.jwt.secret=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "app.jwt.refresh-token-encryption-key=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "payment.toss.secret-key=test-only-key"
})
class MultiOptionPurchaseIntegrationTest {
    @Autowired OrderService orders;
    @Autowired CartService carts;
    @Autowired PaymentService payments;
    @Autowired PaymentCancellationService cancellations;
    @Autowired UserRepository users;
    @Autowired SellerRepository sellers;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired ProductOptionGroupRepository groups;
    @Autowired ProductOptionValueRepository values;
    @Autowired ProductVariantOptionValueRepository mappings;
    @Autowired OrderItemRepository orderItems;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean PaymentGatewayRegistry gateways;
    @Autowired PaymentTransactionService paymentTransactions;
    @Autowired PaymentExpirationTransactionService expiration;
    @Autowired com.giftmarket.product.service.ProductOptionService optionService;
    @Autowired com.giftmarket.product.service.ProductVariantService variantService;
    @Autowired com.giftmarket.product.service.ProductService productService;

    @Test
    void competingDefaultStockEditsRejectTheStaleSellerVersion() throws Exception {
        Plain f = plain(true, 10);
        Long version = products.findById(f.product()).orElseThrow().getVersion();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                start.await();
                try {
                    productService.updateProductStock(f.sellerUser(), f.product(),
                            new com.giftmarket.product.dto.request.ProductStockUpdateRequest(9, version));
                    return true;
                } catch (com.giftmarket.product.exception.ProductConflictException expected) { return false; }
            })).toList();
            start.countDown();
            int successes = 0;
            for (var future : futures) if (future.get(30, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertPlain(f, 9, 9);
    }

    @Test
    void conversionAndPurchaseSerializeWithoutStrandingAReservation() throws Exception {
        Plain f = plain(true, 10);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var purchase = pool.submit(() -> {
                start.await();
                try { orders.createDirectOrder(f.buyer(), plainOrder(f, 2)); return true; }
                catch (RuntimeException expected) { return false; }
            });
            var conversion = pool.submit(() -> {
                start.await();
                try { convert(f); return true; }
                catch (com.giftmarket.product.exception.ProductConflictException expected) { return false; }
            });
            start.countDown();
            boolean bought = purchase.get(30, java.util.concurrent.TimeUnit.SECONDS);
            boolean converted = conversion.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(bought).isNotEqualTo(converted);
            assertPlain(f, bought ? 8 : 10, bought ? 8 : 3);
            assertThat(variants.findById(f.variant()).orElseThrow().isActive()).isEqualTo(bought);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"cancel", "failure", "expiry"})
    void defaultSkuReservationsRestoreExactlyOnce(String outcome) {
        Plain f = plain(true, 10);
        var request = plainOrder(f, 2);
        var prepared = orders.createDirectOrder(f.buyer(), request);
        assertThat(orders.createDirectOrder(f.buyer(), request).orderId()).isEqualTo(prepared.orderId());
        tx().executeWithoutResult(status -> {
            var item = orderItems.findAllByOrderIdOrderByIdAsc(prepared.orderId()).getFirst();
            assertThat(item.getVariant().getId()).isEqualTo(f.variant());
            assertThat(item.getOptionSnapshot()).isNull();
        });
        assertPlain(f, 8, 8);
        if (outcome.equals("cancel")) {
            var cancel = new OrderCancelRequest(UUID.randomUUID().toString(), "미결제 취소");
            cancellations.cancel(f.buyer(), prepared.orderId(), cancel);
            cancellations.cancel(f.buyer(), prepared.orderId(), cancel);
        } else if (outcome.equals("failure")) {
            paymentTransactions.startConfirm(f.buyer(), prepared.paymentId(), new PaymentConfirmRequest(
                    "default-failure", prepared.merchantPaymentId(), prepared.totalAmount()));
            paymentTransactions.fail(f.buyer(), prepared.paymentId(), "DECLINED", "거절", "ABORTED");
            paymentTransactions.fail(f.buyer(), prepared.paymentId(), "DECLINED", "중복", "ABORTED");
        } else {
            assertThat(expiration.expireReadyPayment(prepared.paymentId(), LocalDateTime.now().plusDays(2))).isTrue();
            assertThat(expiration.expireReadyPayment(prepared.paymentId(), LocalDateTime.now().plusDays(2))).isFalse();
        }
        assertPlain(f, 10, 10);
    }

    @Test
    void defaultCartIsMergedAndOrderedThroughVariantWithoutOptionUi() {
        Plain f = plain(true, 10);
        carts.addCartItem(f.buyer(), new CartItemCreateRequest(f.product(), null, 1));
        var cart = carts.addCartItem(f.buyer(), new CartItemCreateRequest(f.product(), null, 1));
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().getFirst().getOptions()).isEmpty();
        var ordered = orders.createOrder(f.buyer(), new OrderCreateRequest(UUID.randomUUID().toString(),
                List.of(cart.getItems().getFirst().getCartItemId()), "수령인", "010-1234-5678", "12345", "서울", null));
        assertPlain(f, 8, 8);
        tx().executeWithoutResult(status -> assertThat(orderItems.findAllByOrderIdOrderByIdAsc(ordered.orderId())
                .getFirst().getVariant().getId()).isEqualTo(f.variant()));
    }

    @Test
    void legacyNullOrderRestoresToDefaultAfterMigrationWithoutChangingSnapshot() {
        Plain legacy = plain(false, 10);
        var prepared = orders.createDirectOrder(legacy.buyer(), plainOrder(legacy, 2));
        Long variantId = tx().execute(status -> variants.save(ProductVariant.createDefault(
                products.findById(legacy.product()).orElseThrow())).getId());
        Plain migrated = new Plain(legacy.buyer(), legacy.sellerUser(), legacy.product(), variantId);
        cancellations.cancel(legacy.buyer(), prepared.orderId(), new OrderCancelRequest(UUID.randomUUID().toString(), "이관 후 취소"));
        assertPlain(migrated, 10, 10);
        tx().executeWithoutResult(status -> {
            var item = orderItems.findAllByOrderIdOrderByIdAsc(prepared.orderId()).getFirst();
            assertThat(item.getVariant()).isNull();
            assertThat(item.getOptionSnapshot()).isNull();
            assertThat(item.getUnitPrice()).isEqualTo(5000);
        });
    }

    @Test
    void paidOrderCanBeRefundedAfterConversionIntoRetainedDefaultOnly() {
        Plain f = plain(true, 10);
        var prepared = orders.createDirectOrder(f.buyer(), plainOrder(f, 2));
        assertThatThrownBy(() -> convert(f)).hasMessageContaining("결제 대기");
        assertPlain(f, 8, 8);
        PaymentGateway gateway = mock(PaymentGateway.class);
        given(gateways.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.confirm(any())).willReturn(new GatewayConfirmResult(GatewayPaymentStatus.PAID,
                "default-paid", "default-confirm", prepared.merchantPaymentId(), prepared.totalAmount(), "KRW",
                PaymentMethod.CARD, null, "DONE", LocalDateTime.now()));
        payments.confirm(f.buyer(), prepared.paymentId(), new PaymentConfirmRequest(
                "default-paid", prepared.merchantPaymentId(), prepared.totalAmount()));
        String sku = variants.findById(f.variant()).orElseThrow().getSkuCode();
        convert(f);
        assertPlain(f, 8, 3);
        assertThat(variants.findById(f.variant()).orElseThrow().isActive()).isFalse();
        assertThatThrownBy(() -> orders.createDirectOrder(f.buyer(), plainOrder(f, 1))).isInstanceOf(RuntimeException.class);
        given(gateway.cancel(any(GatewayCancelCommand.class))).willReturn(new GatewayCancelResult(
                GatewayPaymentStatus.CANCELED, "default-paid", "default-cancel", prepared.merchantPaymentId(),
                prepared.totalAmount(), 0L, "KRW", "CANCELED", LocalDateTime.now(), prepared.totalAmount(), "DONE", 0L));
        var cancel = new OrderCancelRequest(UUID.randomUUID().toString(), "전환 전 주문 취소");
        cancellations.cancel(f.buyer(), prepared.orderId(), cancel);
        cancellations.cancel(f.buyer(), prepared.orderId(), cancel);
        assertPlain(f, 10, 3);
        assertThat(variants.findById(f.variant()).orElseThrow().getSkuCode()).isEqualTo(sku);
        verify(gateway, times(1)).cancel(any(GatewayCancelCommand.class));
    }

    @Test
    void competingDefaultOrdersCannotOversell() throws Exception {
        Plain f = plain(true, 1);
        Long otherBuyer = fixture().buyer();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                start.await();
                try { orders.createDirectOrder(i == 0 ? f.buyer() : otherBuyer, plainOrder(f, 1)); return true; }
                catch (RuntimeException expected) { return false; }
            })).toList();
            start.countDown();
            int successes = 0;
            for (var future : futures) if (future.get(30, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertPlain(f, 0, 0);
    }

    private void convert(Plain f) {
        tx().executeWithoutResult(status -> {
            var saved = optionService.updateProductOptions(f.sellerUser(), f.product(),
                    new com.giftmarket.product.dto.request.ProductOptionUpdateRequest(List.of(
                            new com.giftmarket.product.dto.request.ProductOptionGroupRequest(null, "색상", 0,
                                    List.of(new com.giftmarket.product.dto.request.ProductOptionValueRequest(null, "빨강", 0)))), true));
            variantService.updateProductVariants(f.sellerUser(), f.product(), new com.giftmarket.product.dto.request.ProductVariantUpdateRequest(
                    List.of(new com.giftmarket.product.dto.request.ProductVariantRequest(null, "NEW-RED",
                            List.of(saved.getOptionGroups().getFirst().getValues().getFirst().getId()), 0L, 3, true))));
        });
    }

    private Plain plain(boolean withDefault, int stock) {
        Fixture source = fixture();
        return tx().execute(status -> {
            var original = products.findById(source.product()).orElseThrow();
            var product = products.save(Product.createDraft(original.getSeller(), original.getCategory(),
                    "무옵션", null, null, "설명", 5000L, stock, null, true, 0L, 1, 3000L, 6000L));
            product.startSale();
            Long variantId = withDefault ? variants.save(ProductVariant.createDefault(product)).getId() : null;
            return new Plain(source.buyer(), original.getSeller().getUser().getId(), product.getId(), variantId);
        });
    }

    private DirectOrderCreateRequest plainOrder(Plain f, int quantity) {
        return new DirectOrderCreateRequest(UUID.randomUUID().toString(), f.product(), null, quantity,
                "수령인", "010-1234-5678", "12345", "서울", null);
    }

    private void assertPlain(Plain f, int retained, int available) {
        assertThat(variants.findById(f.variant()).orElseThrow().getStockQuantity()).isEqualTo(retained);
        assertThat(products.findById(f.product()).orElseThrow().getStockQuantity()).isEqualTo(available);
    }

    private record Plain(Long buyer, Long sellerUser, Long product, Long variant) {}

    @Test
    void multiOptionPaymentAndCancellationPreserveStockAndIdentityExactlyOnce() {
        Fixture f = fixture();
        var request = direct(f, List.of(new DirectOrderItemRequest(f.large(), 2), new DirectOrderItemRequest(f.small(), 2)));
        var prepared = orders.createDirectOrder(f.buyer(), request);
        assertThat(prepared.totalProductAmount()).isEqualTo(44_000L);
        assertThat(prepared.totalShippingFee()).isEqualTo(6_000L);
        assertThat(prepared.totalAmount()).isEqualTo(50_000L);
        assertStocks(f, 8, 8);
        assertThat(orders.createDirectOrder(f.buyer(), request).orderId()).isEqualTo(prepared.orderId());
        tx().executeWithoutResult(status -> {
            var items = orderItems.findAllByOrderIdOrderByIdAsc(prepared.orderId());
            assertThat(items).hasSize(2);
            assertThat(items).extracting(OrderItem::getQuantity).containsExactly(2, 2);
            assertThat(items).extracting(OrderItem::getOptionSnapshot).containsExactly("크기: 소형", "크기: 대형");
            assertThat(items).allSatisfy(item -> assertThat(item.getSourceCartItemId()).isNull());
        });
        PaymentGateway gateway = mock(PaymentGateway.class);
        given(gateways.get(PaymentProvider.TOSS)).willReturn(gateway);
        given(gateway.confirm(any())).willReturn(new GatewayConfirmResult(GatewayPaymentStatus.PAID,
                "multi-key", "multi-confirm", prepared.merchantPaymentId(), prepared.totalAmount(), "KRW",
                PaymentMethod.CARD, null, "DONE", LocalDateTime.now()));
        payments.confirm(f.buyer(), prepared.paymentId(), new PaymentConfirmRequest(
                "multi-key", prepared.merchantPaymentId(), prepared.totalAmount()));
        assertStocks(f, 8, 8);
        given(gateway.cancel(any(GatewayCancelCommand.class))).willReturn(new GatewayCancelResult(
                GatewayPaymentStatus.CANCELED, "multi-key", "multi-cancel", prepared.merchantPaymentId(),
                prepared.totalAmount(), 0L, "KRW", "CANCELED", LocalDateTime.now(), prepared.totalAmount(), "DONE", 0L));
        var cancel = new OrderCancelRequest(UUID.randomUUID().toString(), "전체 취소");
        assertThat(cancellations.cancel(f.buyer(), prepared.orderId(), cancel).orderStatus()).isEqualTo(OrderStatus.CANCELLED);
        cancellations.cancel(f.buyer(), prepared.orderId(), cancel);
        assertStocks(f, 10, 10);
        verify(gateway, times(1)).cancel(any(GatewayCancelCommand.class));
    }

    @Test
    void bulkCartMergesThenOrdersAllOptionsUsingExistingCartOrderFlow() {
        Fixture f = fixture();
        carts.addCartItem(f.buyer(), item(f, f.small(), 1));
        var cart = carts.addCartItems(f.buyer(), List.of(item(f, f.small(), 1), item(f, f.large(), 2)));
        assertThat(cart.getItems()).hasSize(2).allSatisfy(item -> assertThat(item.getQuantity()).isEqualTo(2));
        assertStocks(f, 10, 10);
        var order = orders.createOrder(f.buyer(), new OrderCreateRequest(UUID.randomUUID().toString(),
                cart.getItems().stream().map(com.giftmarket.cart.dto.response.CartItemResponse::getCartItemId).toList(),
                "구매자", "010-1234-5678", "12345", "서울시", null));
        assertThat(order.totalAmount()).isEqualTo(50_000L);
        assertStocks(f, 8, 8);
    }

    @Test
    void bulkFailureRollsBackBothExistingQuantityAndNewRows() {
        Fixture f = fixture();
        carts.addCartItem(f.buyer(), item(f, f.small(), 1));
        assertThatThrownBy(() -> carts.addCartItems(f.buyer(), List.of(
                item(f, f.small(), 1), item(f, f.large(), 11)))).isInstanceOf(RuntimeException.class);
        var cart = carts.getCart(f.buyer());
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().getFirst().getQuantity()).isEqualTo(1);
        assertThatThrownBy(() -> carts.addCartItems(f.buyer(), List.of(
                item(f, f.large(), 2), item(f, f.small(), 10)))).isInstanceOf(RuntimeException.class);
        assertThat(carts.getCart(f.buyer()).getItems()).hasSize(1);
        assertStocks(f, 10, 10);
    }

    @Test
    void changedPriceRollsBackEntireBulkRequest() {
        Fixture f = fixture();
        assertThatThrownBy(() -> carts.addCartItems(f.buyer(), List.of(item(f, f.small(), 2),
                new CartItemCreateRequest(f.product(), f.large(), 2, 10_000L))))
                .hasMessageContaining("가격이 변경");
        assertThat(carts.getCart(f.buyer()).getItems()).isEmpty();
        assertStocks(f, 10, 10);
    }

    @Test
    void archivedAndSoldOutOptionsBlockWholeCartAndDirectPurchase() {
        for (boolean archived : List.of(true, false)) {
            Fixture f = fixture();
            tx().executeWithoutResult(status -> {
                var large = variants.findById(f.large()).orElseThrow();
                if (archived) large.deactivate(); else large.changeStockQuantity(0);
            });
            assertThatThrownBy(() -> carts.addCartItems(f.buyer(), List.of(
                    item(f, f.small(), 2), item(f, f.large(), 2)))).isInstanceOf(RuntimeException.class);
            assertThat(carts.getCart(f.buyer()).getItems()).isEmpty();
            assertThatThrownBy(() -> orders.createDirectOrder(f.buyer(), direct(f, List.of(
                    new DirectOrderItemRequest(f.small(), 2), new DirectOrderItemRequest(f.large(), 2)))))
                    .isInstanceOf(RuntimeException.class);
            assertThat(variants.findById(f.small()).orElseThrow().getStockQuantity()).isEqualTo(10);
        }
    }

    @Test
    void duplicateAndOverStockDirectRequestsLeaveAllStockUnchanged() {
        Fixture f = fixture();
        for (var items : List.of(List.of(new DirectOrderItemRequest(f.small(), 2), new DirectOrderItemRequest(f.small(), 2)),
                List.of(new DirectOrderItemRequest(f.small(), 2), new DirectOrderItemRequest(f.large(), 11)))) {
            assertThatThrownBy(() -> orders.createDirectOrder(f.buyer(), direct(f, items))).isInstanceOf(RuntimeException.class);
            assertStocks(f, 10, 10);
        }
    }

    private CartItemCreateRequest item(Fixture f, Long variant, int quantity) {
        return new CartItemCreateRequest(f.product(), variant, quantity, variant.equals(f.small()) ? 10_000L : 12_000L);
    }

    @Test
    void singleVariantAndPlainProductKeepBothLegacyAndListRequestSupport() {
        Fixture f = fixture();
        var single = orders.createDirectOrder(f.buyer(), new DirectOrderCreateRequest(
                UUID.randomUUID().toString(), f.product(), f.small(), 2,
                "구매자", "010-1234-5678", "12345", "서울시", null));
        assertThat(single.totalProductAmount()).isEqualTo(20_000L);
        assertStocks(f, 8, 10);
        Long plainId = tx().execute(status -> {
            var source = products.findById(f.product()).orElseThrow();
            var plain = products.save(Product.createDraft(source.getSeller(), source.getCategory(),
                    "무옵션 상품", null, null, "설명", 5_000L, 10, null, true, 0L, 1, 3_000L, 6_000L));
            plain.startSale();
            return plain.getId();
        });
        var plainCart = carts.addCartItems(f.buyer(), List.of(new CartItemCreateRequest(plainId, null, 2, 5_000L)));
        assertThat(plainCart.getItems()).hasSize(1);
        var plain = orders.createDirectOrder(f.buyer(), new DirectOrderCreateRequest(
                UUID.randomUUID().toString(), plainId, null, null,
                "구매자", "010-1234-5678", "12345", "서울시", null,
                List.of(new DirectOrderItemRequest(null, 2))));
        assertThat(plain.totalAmount()).isEqualTo(10_000L);
        assertThat(products.findById(plainId).orElseThrow().getStockQuantity()).isEqualTo(8);
        cancellations.cancel(f.buyer(), plain.orderId(), new OrderCancelRequest(UUID.randomUUID().toString(), "미결제 취소"));
        assertThat(products.findById(plainId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void archivedOptionValueBlocksBothPurchasePathsEvenWhenVariantIsActive() {
        Fixture f = fixture();
        tx().executeWithoutResult(status -> mappings.findAllByVariantId(f.large()).getFirst().getOptionValue().archive());
        assertThatThrownBy(() -> carts.addCartItems(f.buyer(), List.of(item(f, f.small(), 2), item(f, f.large(), 2))))
                .hasMessageContaining("보관");
        assertThat(carts.getCart(f.buyer()).getItems()).isEmpty();
        assertThatThrownBy(() -> orders.createDirectOrder(f.buyer(), direct(f, List.of(
                new DirectOrderItemRequest(f.small(), 2), new DirectOrderItemRequest(f.large(), 2)))))
                .hasMessageContaining("보관");
        assertStocks(f, 10, 10);
    }

    private DirectOrderCreateRequest direct(Fixture f, List<DirectOrderItemRequest> items) {
        return new DirectOrderCreateRequest(UUID.randomUUID().toString(), f.product(), null, null,
                "구매자", "010-1234-5678", "12345", "서울시", null, items);
    }

    private void assertStocks(Fixture f, int small, int large) {
        var s = variants.findById(f.small()).orElseThrow();
        var l = variants.findById(f.large()).orElseThrow();
        assertThat(s.getStockQuantity()).isEqualTo(small);
        assertThat(l.getStockQuantity()).isEqualTo(large);
        assertThat(products.findById(f.product()).orElseThrow().getStockQuantity()).isEqualTo(small + large);
        assertThat(s.getSkuCode()).isEqualTo(f.smallSku());
        assertThat(l.getSkuCode()).isEqualTo(f.largeSku());
    }

    private TransactionTemplate tx() { return new TransactionTemplate(transactionManager); }

    private Fixture fixture() {
        return tx().execute(status -> {
            String key = UUID.randomUUID().toString();
            User buyer = users.save(User.createOAuthUser(key + "@test.invalid", "구매자", null, AuthProvider.GOOGLE, key));
            User owner = users.save(User.createOAuthUser(key + "s@test.invalid", "판매자", null, AuthProvider.GOOGLE, key + "s"));
            Seller seller = sellers.save(Seller.create(owner, "테스트 상점", "테스트"));
            Category category = categories.save(Category.create(null, "테스트", 1));
            Product product = products.save(Product.createDraft(seller, category, "다중 옵션 상품", null, null, "설명",
                    10_000L, 20, null, false, 3_000L, 1, 3_000L, 6_000L));
            ProductOptionGroup group = groups.save(ProductOptionGroup.create(product, "크기", 0));
            ProductOptionValue smallValue = values.save(ProductOptionValue.create(group, "소형", 0));
            ProductOptionValue largeValue = values.save(ProductOptionValue.create(group, "대형", 1));
            ProductVariant small = variants.save(ProductVariant.create(product, "SMALL", group.getId() + ":" + smallValue.getId(), 0L, 10));
            ProductVariant large = variants.save(ProductVariant.create(product, "LARGE", group.getId() + ":" + largeValue.getId(), 2_000L, 10));
            mappings.save(ProductVariantOptionValue.create(small, smallValue));
            mappings.save(ProductVariantOptionValue.create(large, largeValue));
            product.startSale();
            return new Fixture(buyer.getId(), product.getId(), small.getId(), large.getId(), small.getSkuCode(), large.getSkuCode());
        });
    }
    private record Fixture(Long buyer, Long product, Long small, Long large, String smallSku, String largeSku) {}
}
