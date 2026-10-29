package com.giftmarket.order.integration;

import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.order.dto.request.DirectOrderCreateRequest;
import com.giftmarket.order.dto.request.OrderCreateRequest;
import com.giftmarket.order.dto.request.OrderCancelRequest;
import com.giftmarket.order.dto.request.OrderCancellationCreateRequest;
import com.giftmarket.order.dto.request.OrderCancellationItemRequest;
import com.giftmarket.order.dto.response.OrderCreateResponse;
import com.giftmarket.order.dto.response.OrderCancellationResponse;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.service.OrderService;
import com.giftmarket.order.service.OrderCancellationWorkflowService;
import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentMethod;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.infrastructure.toss.TossPaymentClient;
import com.giftmarket.payment.repository.PaymentRepository;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.service.PaymentCancellationService;
import com.giftmarket.payment.service.PaymentService;
import com.giftmarket.payment.service.PartialPaymentCancellationReconciliationService;
import com.giftmarket.product.entity.Category;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.service.InitialSettlementLedgerService;
import com.giftmarket.settlement.service.CancellationSettlementLedgerService;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("mysql-container")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "spring.config.location=optional:classpath:/isolated-mysql-test/",
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false",
        "spring.task.scheduling.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=isolated-client",
        "spring.security.oauth2.client.registration.google.client-secret=isolated-secret",
        "app.frontend-url=http://127.0.0.1",
        "app.jwt.secret=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "app.jwt.refresh-token-encryption-key=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "minio.endpoint=http://127.0.0.1:9",
        "minio.access-key=isolated-access",
        "minio.secret-key=isolated-secret",
        "minio.bucket=isolated-test",
        "payment.toss.secret-key=isolated-test",
        "payment.toss.base-url=http://127.0.0.1:9"
})
@ActiveProfiles("mysql-container")
@ContextConfiguration(initializers = MySqlOrderConcurrencyIntegrationTest.NoEnvironment.class)
@Import(MySqlOrderConcurrencyIntegrationTest.ContainerDataSource.class)
class MySqlOrderConcurrencyIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("giftmarket_test")
            .withUsername("isolated_test")
            .withPassword("isolated_test_password");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    static class NoEnvironment implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerDataSource {
        @Bean(destroyMethod = "close")
        @Primary
        HikariDataSource dataSource() {
            if (!MYSQL.isRunning()) throw new IllegalStateException("MySQL test container is not running");
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(MYSQL.getJdbcUrl());
            dataSource.setUsername(MYSQL.getUsername());
            dataSource.setPassword(MYSQL.getPassword());
            dataSource.setDriverClassName(MYSQL.getDriverClassName());
            return dataSource;
        }
    }

    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired PaymentCancellationService cancellationService;
    @Autowired TransactionTemplate transactions;
    @Autowired EntityManager entityManager;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired SellerOrderRepository sellerOrders;
    @Autowired OrderCancellationRepository cancellations;
    @Autowired PaymentCancellationRepository refunds;
    @Autowired OrderCancellationWorkflowService partialCancellation;
    @Autowired PartialPaymentCancellationReconciliationService partialReconciliation;
    @MockitoSpyBean PaymentRepository payments;
    @MockitoBean PaymentGatewayRegistry gatewayRegistry;
    @MockitoBean TossPaymentClient tossPaymentClient;
    @MockitoBean StorageService storageService;
    @MockitoBean InitialSettlementLedgerService ledger;
    @MockitoBean CancellationSettlementLedgerService cancellationLedger;

    @Test
    void twoBuyersCompeteForOneUnit() throws Exception {
        Fixture f = fixture();
        long ordersBefore = orders.count();
        List<Object> results = concurrently(
                () -> orderService.createDirectOrder(f.buyer1(), request(f.productId())),
                () -> orderService.createDirectOrder(f.buyer2(), request(f.productId())));
        assertThat(results.stream().filter(OrderCreateResponse.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(OrderException.class::isInstance)).hasSize(1);
        assertThat(orders.count()).isEqualTo(ordersBefore + 1);
        assertThat(products.findById(f.productId()).orElseThrow().getStockQuantity()).isZero();
    }

    @Test
    void concurrentCancellationRestoresStockOnce() throws Exception {
        Fixture f = fixture();
        OrderCreateResponse order = orderService.createDirectOrder(f.buyer1(), request(f.productId()));
        OrderCancelRequest cancel = new OrderCancelRequest(UUID.randomUUID().toString(), "취소 요청");
        List<Object> results = concurrently(
                () -> cancellationService.cancel(f.buyer1(), order.orderId(), cancel),
                () -> cancellationService.cancel(f.buyer1(), order.orderId(), cancel));
        assertThat(results).noneMatch(Throwable.class::isInstance);
        assertThat(results).allSatisfy(result -> {
            assertThat(result).isInstanceOf(com.giftmarket.order.dto.response.OrderCancelResponse.class);
            assertThat(((com.giftmarket.order.dto.response.OrderCancelResponse) result).orderStatus()).isEqualTo(OrderStatus.CANCELLED);
        });
        assertThat(orders.findById(order.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(products.findById(f.productId()).orElseThrow().getStockQuantity()).isEqualTo(1);
        assertThat(cancellationService.cancel(f.buyer1(), order.orderId(), cancel).orderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(products.findById(f.productId()).orElseThrow().getStockQuantity()).isEqualTo(1);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void paymentInsertFailureRollsBackOrderAndStock() {
        Fixture f = fixture();
        long ordersBefore = orders.count();
        doThrow(new DataIntegrityViolationException("forced test failure")).when(payments).save(any(Payment.class));
        try {
            assertThatThrownBy(() -> orderService.createDirectOrder(f.buyer1(), request(f.productId())))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            reset(payments);
        }
        assertThat(orders.count()).isEqualTo(ordersBefore);
        assertThat(products.findById(f.productId()).orElseThrow().getStockQuantity()).isEqualTo(1);
    }

    @Test
    void gatewayApprovalWithDatabaseFailureIsRecoveredByQuery() {
        Fixture f = fixture();
        OrderCreateResponse order = orderService.createDirectOrder(f.buyer1(), request(f.productId()));
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gatewayRegistry.get(PaymentProvider.TOSS)).thenReturn(gateway);
        LocalDateTime now = LocalDateTime.now();
        when(gateway.confirm(any())).thenReturn(new GatewayConfirmResult(GatewayPaymentStatus.PAID,
                "isolated-payment-key", "isolated-transaction", order.merchantPaymentId(), order.totalAmount(),
                "KRW", PaymentMethod.CARD, null, "DONE", now));
        doThrow(new DataIntegrityViolationException("forced database write failure")).when(ledger).recordInitialSales(any(), any());
        assertThatThrownBy(() -> paymentService.confirm(f.buyer1(), order.paymentId(),
                new PaymentConfirmRequest("isolated-payment-key", order.merchantPaymentId(), order.totalAmount())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(payments.findById(order.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CONFIRMING);
        assertThat(orders.findById(order.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        reset(ledger);
        when(gateway.getPayment("isolated-payment-key")).thenReturn(new GatewayPaymentQueryResult(
                GatewayPaymentStatus.PAID, "isolated-payment-key", "isolated-transaction", order.merchantPaymentId(),
                order.totalAmount(), "KRW", PaymentMethod.CARD, null, "DONE", now));
        assertThat(paymentService.getPayment(f.buyer1(), order.paymentId()).status()).isEqualTo(PaymentStatus.PAID);
        assertThat(orders.findById(order.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(products.findById(f.productId()).orElseThrow().getStockQuantity()).isZero();
        verify(gateway, times(1)).confirm(any());
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void duplicatePartialCancellationRequestsRefundAndRestockOnlyOnce() throws Exception {
        PaidFixture f = paidFixture();
        OrderCancellationCreateRequest request = partialRequest(f.sellerOrderId(), f.item1(), UUID.randomUUID().toString());
        f.pg().holdFirstCancellation = true;
        List<Object> results = concurrentlyWhileFirstRefundIsPending(f.pg(),
                () -> partialCancellation.create(f.buyerId(), f.order().orderId(), request),
                () -> partialCancellation.create(f.buyerId(), f.order().orderId(), request));
        assertThat(results).allMatch(OrderCancellationResponse.class::isInstance);
        var responses = results.stream().map(OrderCancellationResponse.class::cast).toList();
        assertThat(responses.get(0).cancellationId()).isEqualTo(responses.get(1).cancellationId());
        assertPartialState(f, 1, 0, 10_000L, 1);
        assertThat(f.pg().cancelCalls).isEqualTo(1);
    }

    @Test
    void differentItemsCancelConcurrentlyWithoutDuplicateRefund() throws Exception {
        PaidFixture f = paidFixture();
        List<Object> results = concurrently(
                () -> partialCancellation.create(f.buyerId(), f.order().orderId(), partialRequest(f.sellerOrderId(), f.item1(), UUID.randomUUID().toString())),
                () -> partialCancellation.create(f.buyerId(), f.order().orderId(), partialRequest(f.sellerOrderId(), f.item2(), UUID.randomUUID().toString())));
        assertThat(results).allMatch(OrderCancellationResponse.class::isInstance);
        assertPartialState(f, 1, 1, 20_000L, 2);
        assertThat(f.pg().cancelCalls).isEqualTo(2);
        assertThat(f.pg().results).hasSize(2);
    }

    @Test
    void retryCompletedPartialCancellationDoesNotRefundAgain() {
        PaidFixture f = paidFixture();
        OrderCancellationCreateRequest request = partialRequest(f.sellerOrderId(), f.item1(), UUID.randomUUID().toString());
        OrderCancellationResponse first = partialCancellation.create(f.buyerId(), f.order().orderId(), request);
        OrderCancellationResponse retry = partialCancellation.create(f.buyerId(), f.order().orderId(), request);
        assertThat(retry.cancellationId()).isEqualTo(first.cancellationId());
        assertThat(retry.status()).isEqualTo(OrderCancellationStatus.COMPLETED);
        assertPartialState(f, 1, 0, 10_000L, 1);
        assertThat(f.pg().cancelCalls).isEqualTo(1);
    }

    @Test
    void pgRefundSuccessDatabaseFailureRemainsRecoverable() {
        PaidFixture f = paidFixture();
        doThrow(new DataIntegrityViolationException("forced ledger write failure"))
                .when(cancellationLedger).recordCancellation(any(), any(), any());
        OrderCancellationCreateRequest request = partialRequest(f.sellerOrderId(), f.item1(), UUID.randomUUID().toString());
        assertThatThrownBy(() -> partialCancellation.create(f.buyerId(), f.order().orderId(), request))
                .isInstanceOf(DataIntegrityViolationException.class);
        var cancellation = cancellations.findByClientRequestKey(request.clientRequestKey()).orElseThrow();
        var refund = refunds.findByOrderCancellationId(cancellation.getId()).orElseThrow();
        assertThat(cancellation.getStatus()).isEqualTo(OrderCancellationStatus.PROCESSING);
        assertThat(refund.getStatus()).isEqualTo(PaymentCancellationStatus.REQUESTED);
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(1);
        assertThat(f.pg().cancelCalls).isEqualTo(1);
        reset(cancellationLedger);
        partialReconciliation.reconcileFromWebhook(f.order().paymentId(), f.pg().query());
        assertPartialState(f, 1, 0, 10_000L, 1);
        assertThat(f.pg().cancelCalls).isEqualTo(1);
    }

    private void assertPartialState(PaidFixture f, int canceled1, int canceled2, long refunded, int transactions) {
        assertThat(orders.findById(f.order().orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payments.findById(f.order().paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PARTIALLY_CANCELED);
        assertThat(orderItems.findById(f.item1()).orElseThrow().getCanceledQuantity()).isEqualTo(canceled1);
        assertThat(orderItems.findById(f.item2()).orElseThrow().getCanceledQuantity()).isEqualTo(canceled2);
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(1 + canceled1);
        assertThat(products.findById(f.product2()).orElseThrow().getStockQuantity()).isEqualTo(1 + canceled2);
        assertThat(refunds.sumAmountByPaymentIdAndStatus(f.order().paymentId(), PaymentCancellationStatus.SUCCEEDED)).isEqualTo(refunded);
        assertThat(refunds.countByPaymentIdAndStatus(f.order().paymentId(), PaymentCancellationStatus.SUCCEEDED)).isEqualTo(transactions);
        assertThat(f.pg().refunded).isEqualTo(refunded);
        var recorded = cancellations.findAllByOrderIdOrderByRequestedAtDescIdDesc(f.order().orderId());
        assertThat(recorded).hasSize(transactions).allMatch(value -> value.getStatus() == OrderCancellationStatus.COMPLETED);
        for (var cancellation : recorded) {
            var refund = refunds.findByOrderCancellationId(cancellation.getId()).orElseThrow();
            assertThat(f.pg().results).containsKey(refund.getIdempotencyKey());
        }
    }

    private OrderCancellationCreateRequest partialRequest(Long sellerOrderId, Long itemId, String key) {
        return new OrderCancellationCreateRequest(key, sellerOrderId, "부분취소", List.of(new OrderCancellationItemRequest(itemId, 1)));
    }

    private PaidFixture paidFixture() {
        String unique = UUID.randomUUID().toString();
        long[] ids = transactions.execute(status -> {
            User owner = User.createOAuthUser("owner-" + unique + "@example.test", "판매자", null, AuthProvider.GOOGLE, "owner-" + unique);
            User buyer = User.createOAuthUser("buyer-" + unique + "@example.test", "구매자", null, AuthProvider.GOOGLE, "buyer-" + unique);
            entityManager.persist(owner);
            entityManager.persist(buyer);
            Seller seller = Seller.create(owner, "상점 " + unique, null);
            entityManager.persist(seller);
            Category category = Category.create(null, "분류 " + unique, 1);
            entityManager.persist(category);
            Product first = Product.createDraft(seller, category, "상품1", "브랜드", "요약", "설명", 10_000L, 3, null, true, 0L, 1, 3_000L, 6_000L);
            Product second = Product.createDraft(seller, category, "상품2", "브랜드", "요약", "설명", 10_000L, 3, null, true, 0L, 1, 3_000L, 6_000L);
            first.startSale(); second.startSale();
            entityManager.persist(first); entityManager.persist(second);
            CartItem cart1 = CartItem.create(buyer, first, null, 2);
            CartItem cart2 = CartItem.create(buyer, second, null, 2);
            entityManager.persist(cart1); entityManager.persist(cart2);
            entityManager.flush();
            return new long[]{buyer.getId(), first.getId(), second.getId(), cart1.getId(), cart2.getId()};
        });
        OrderCreateResponse order = orderService.createOrder(ids[0], new OrderCreateRequest(UUID.randomUUID().toString(),
                List.of(ids[3], ids[4]), "구매자", "010-1234-5678", "12345", "서울", null));
        FakePg pg = new FakePg("pg-" + unique, order);
        when(gatewayRegistry.get(PaymentProvider.TOSS)).thenReturn(pg.gateway);
        paymentService.confirm(ids[0], order.paymentId(), new PaymentConfirmRequest(pg.key, order.merchantPaymentId(), order.totalAmount()));
        var items = orderItems.findAllByOrderIdOrderByIdAsc(order.orderId());
        Long firstItem = items.stream().filter(item -> item.getProduct().getId().equals(ids[1])).map(OrderItem::getId).findFirst().orElseThrow();
        Long secondItem = items.stream().filter(item -> item.getProduct().getId().equals(ids[2])).map(OrderItem::getId).findFirst().orElseThrow();
        Long sellerOrder = sellerOrders.findAllByOrderIdOrderByIdAsc(order.orderId()).getFirst().getId();
        return new PaidFixture(ids[0], ids[1], ids[2], firstItem, secondItem, sellerOrder, order, pg);
    }

    private record PaidFixture(Long buyerId, Long product1, Long product2, Long item1, Long item2,
                               Long sellerOrderId, OrderCreateResponse order, FakePg pg) { }

    private static class FakePg {
        final PaymentGateway gateway = mock(PaymentGateway.class);
        final String key;
        final OrderCreateResponse order;
        final Map<String, GatewayCancelResult> results = new HashMap<>();
        final List<GatewayCancellationTransaction> cancellationTransactions = new ArrayList<>();
        final CountDownLatch cancelEntered = new CountDownLatch(1);
        final CountDownLatch releaseCancel = new CountDownLatch(1);
        final AtomicBoolean firstCancel = new AtomicBoolean(true);
        volatile boolean holdFirstCancellation;
        long refunded;
        int cancelCalls;
        FakePg(String key, OrderCreateResponse order) {
            this.key = key; this.order = order;
            when(gateway.confirm(any())).thenAnswer(invocation -> new GatewayConfirmResult(GatewayPaymentStatus.PAID,
                    key, "approval", order.merchantPaymentId(), order.totalAmount(), "KRW", PaymentMethod.CARD, null, "DONE", LocalDateTime.now()));
            when(gateway.getPayment(key)).thenAnswer(invocation -> query());
            when(gateway.cancel(any())).thenAnswer(invocation -> {
                if (holdFirstCancellation && firstCancel.compareAndSet(true, false)) {
                    cancelEntered.countDown();
                    if (!releaseCancel.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("PG test gate timed out");
                }
                return cancel(invocation.getArgument(0));
            });
        }
        synchronized GatewayPaymentQueryResult query() {
            return new GatewayPaymentQueryResult(refunded == 0 ? GatewayPaymentStatus.PAID : GatewayPaymentStatus.PARTIALLY_CANCELED,
                    key, "approval", order.merchantPaymentId(), order.totalAmount(), "KRW", PaymentMethod.CARD, null,
                    "DONE", LocalDateTime.now(), order.totalAmount() - refunded, null, true, List.copyOf(cancellationTransactions));
        }
        synchronized GatewayCancelResult cancel(GatewayCancelCommand command) {
            cancelCalls++;
            if (results.containsKey(command.idempotencyKey())) return results.get(command.idempotencyKey());
            refunded += command.cancelAmount();
            long remaining = command.amount() - refunded;
            String tx = "refund-" + UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now().plusSeconds(1);
            var result = new GatewayCancelResult(GatewayPaymentStatus.PARTIALLY_CANCELED, key, tx, order.merchantPaymentId(),
                    order.totalAmount(), remaining, "KRW", "PARTIAL_CANCELED", now, command.cancelAmount(), "DONE", remaining);
            cancellationTransactions.add(new GatewayCancellationTransaction(tx, command.cancelAmount(), command.reason(), "DONE", now, remaining));
            results.put(command.idempotencyKey(), result);
            return result;
        }
    }

    private Fixture fixture() {
        return transactions.execute(status -> {
            String id = UUID.randomUUID().toString();
            User owner = User.createOAuthUser("owner-" + id + "@example.test", "판매자", null, AuthProvider.GOOGLE, "owner-" + id);
            User buyer1 = User.createOAuthUser("buyer1-" + id + "@example.test", "구매자1", null, AuthProvider.GOOGLE, "buyer1-" + id);
            User buyer2 = User.createOAuthUser("buyer2-" + id + "@example.test", "구매자2", null, AuthProvider.GOOGLE, "buyer2-" + id);
            entityManager.persist(owner);
            entityManager.persist(buyer1);
            entityManager.persist(buyer2);
            Seller seller = Seller.create(owner, "상점 " + id, null);
            entityManager.persist(seller);
            Category category = Category.create(null, "테스트 " + id, 1);
            entityManager.persist(category);
            Product product = Product.createDraft(seller, category, "상품", "브랜드", "요약", "설명",
                    10_000L, 1, null, true, 0L, 1, 3_000L, 6_000L);
            product.startSale();
            entityManager.persist(product);
            entityManager.flush();
            return new Fixture(buyer1.getId(), buyer2.getId(), product.getId());
        });
    }

    private DirectOrderCreateRequest request(Long productId) {
        return new DirectOrderCreateRequest(UUID.randomUUID().toString(), productId, null, 1,
                "구매자", "010-1234-5678", "12345", "서울", null);
    }

    private List<Object> concurrently(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Object> one = () -> invoke(first, ready, start);
            Callable<Object> two = () -> invoke(second, ready, start);
            Future<Object> a = executor.submit(one);
            Future<Object> b = executor.submit(two);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }

    private List<Object> concurrentlyWhileFirstRefundIsPending(FakePg pg, Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var completed = new ExecutorCompletionService<Object>(executor);
            completed.submit(() -> invoke(first, ready, start));
            completed.submit(() -> invoke(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(pg.cancelEntered.await(30, TimeUnit.SECONDS)).isTrue();
            Future<Object> firstCompleted = completed.poll(30, TimeUnit.SECONDS);
            assertThat(firstCompleted).as("duplicate request finishes while the first PG call is pending").isNotNull();
            Object firstResult = firstCompleted.get();
            pg.releaseCancel.countDown();
            Future<Object> secondCompleted = completed.poll(30, TimeUnit.SECONDS);
            assertThat(secondCompleted).isNotNull();
            return List.of(firstResult, secondCompleted.get());
        } finally {
            start.countDown();
            pg.releaseCancel.countDown();
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private Object invoke(Callable<?> action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent start timed out");
        try {
            return action.call();
        } catch (Exception exception) {
            return exception;
        }
    }

    private record Fixture(Long buyer1, Long buyer2, Long productId) { }
}
