package com.giftmarket.order.integration;

import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.order.dto.request.DirectOrderCreateRequest;
import com.giftmarket.order.dto.request.OrderCreateRequest;
import com.giftmarket.order.dto.request.OrderCancelRequest;
import com.giftmarket.order.dto.request.OrderCancellationCreateRequest;
import com.giftmarket.order.dto.request.OrderCancellationItemRequest;
import com.giftmarket.order.dto.response.OrderCreateResponse;
import com.giftmarket.order.dto.response.OrderCancellationResponse;
import com.giftmarket.order.dto.request.ReturnRequestCreateRequest;
import com.giftmarket.order.dto.request.ReturnRequestItemRequest;
import com.giftmarket.order.dto.request.ExchangeRequestCreateRequest;
import com.giftmarket.order.dto.request.ExchangeRequestItemRequest;
import com.giftmarket.order.entity.ReturnReasonType;
import com.giftmarket.order.entity.ExchangeReasonType;
import com.giftmarket.order.entity.Shipment;
import com.giftmarket.order.entity.ShipmentType;
import com.giftmarket.order.service.ReturnRequestService;
import com.giftmarket.order.service.ExchangeRequestService;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.order.dto.response.ExchangeRequestResponse;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.service.OrderService;
import com.giftmarket.order.service.PurchaseConfirmationService;
import com.giftmarket.order.service.OrderCancellationWorkflowService;
import com.giftmarket.order.service.SellerOrderManagementService;
import com.giftmarket.order.service.OrderCancellationService;
import com.giftmarket.order.dto.request.SellerOrderCancelRequest;
import com.giftmarket.order.dto.request.SellerOrderShipRequest;
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
        @Bean
        static org.springframework.beans.factory.config.BeanFactoryPostProcessor disableAutomaticScheduling() {
            return factory -> {
                var registry = (org.springframework.beans.factory.support.BeanDefinitionRegistry) factory;
                String name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
            };
        }

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

    @Autowired ReturnRequestService returnRequests;
    @Autowired ExchangeRequestService exchangeRequests;
    @Autowired ReturnRequestRepository returnRepository;
    @Autowired ExchangeRequestRepository exchangeRepository;
    @Autowired OrderService orderService;
    @Autowired PurchaseConfirmationService purchaseConfirmation;
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
    @Autowired SellerOrderManagementService sellerManagement;
    @Autowired OrderCancellationService buyerCancellation;
    @Autowired PartialPaymentCancellationReconciliationService partialReconciliation;
    @MockitoSpyBean PaymentRepository payments;
    @MockitoBean PaymentGatewayRegistry gatewayRegistry;
    @MockitoBean TossPaymentClient tossPaymentClient;
    @MockitoBean StorageService storageService;
    @MockitoSpyBean InitialSettlementLedgerService ledger;
    @MockitoSpyBean CancellationSettlementLedgerService cancellationLedger;
    @Autowired tools.jackson.databind.json.JsonMapper requestJson;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "-1,0,1", "0,0,1", "10000001,0,1", "9223372036854775807,1,1",
            "10000000,1,1", "10000000,0,2147483647", "4611686018427387904,0,2"
    })
    void invalidOrderMoneyIsRejectedBeforeOrderPaymentOrInventoryChanges(long price, long shipping, int quantity) {
        Fixture f = fixture();
        int stock = Math.max(quantity, 2);
        transactions.executeWithoutResult(status -> {
            var product = products.findById(f.productId()).orElseThrow();
            org.springframework.test.util.ReflectionTestUtils.setField(product, "price", price);
            org.springframework.test.util.ReflectionTestUtils.setField(product, "freeShipping", false);
            org.springframework.test.util.ReflectionTestUtils.setField(product, "shippingFee", shipping);
            product.changeStockQuantity(stock);
        });
        long ordersBefore = orders.count(); long paymentsBefore = payments.count();
        var request = new DirectOrderCreateRequest(UUID.randomUUID().toString(), f.productId(), null, quantity,
                "구매자", "01012345678", "12345", "주소", null);
        assertThatThrownBy(() -> orderService.createDirectOrder(f.buyer1(), request))
                .isInstanceOf(com.giftmarket.global.exception.MoneyCalculationException.class);
        assertThat(orders.count()).isEqualTo(ordersBefore);
        assertThat(payments.count()).isEqualTo(paymentsBefore);
        assertThat(products.findById(f.productId()).orElseThrow().getStockQuantity()).isEqualTo(stock);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void orderAcceptsExactPricePlusShippingCeiling() {
        Fixture f = fixture();
        transactions.executeWithoutResult(status -> {
            var product = products.findById(f.productId()).orElseThrow();
            org.springframework.test.util.ReflectionTestUtils.setField(product, "price", 9_999_999L);
            org.springframework.test.util.ReflectionTestUtils.setField(product, "freeShipping", false);
            org.springframework.test.util.ReflectionTestUtils.setField(product, "shippingFee", 1L);
        });
        var order = orderService.createDirectOrder(f.buyer1(), request(f.productId()));
        assertThat(order.totalAmount()).isEqualTo(com.giftmarket.global.util.MoneyLimits.MAX_AMOUNT);
        assertThat(payments.findById(order.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(products.findById(f.productId()).orElseThrow().getStockQuantity()).isZero();
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void laterInvalidSellerItemDoesNotEvenTemporarilyChangeEarlierProductsStock() {
        Fixture first = fixture(); Fixture second = fixture();
        List<Long> cartIds = transactions.execute(status -> {
            var buyer = entityManager.find(User.class, first.buyer1());
            var a = products.findById(first.productId()).orElseThrow();
            var b = products.findById(second.productId()).orElseThrow();
            org.springframework.test.util.ReflectionTestUtils.setField(b, "price", Long.MAX_VALUE);
            var cartA = CartItem.create(buyer, a, null, 1);
            var cartB = CartItem.create(buyer, b, null, 1);
            entityManager.persist(cartA); entityManager.persist(cartB); entityManager.flush();
            return List.of(cartA.getId(), cartB.getId());
        });
        long before = orders.count(); long paymentsBefore = payments.count();
        transactions.executeWithoutResult(status -> {
            var managedFirst = products.findById(first.productId()).orElseThrow();
            try {
                assertThatThrownBy(() -> orderService.createOrder(first.buyer1(), new OrderCreateRequest(
                        UUID.randomUUID().toString(), cartIds, "구매자", "01012345678", "12345", "주소", null)))
                        .isInstanceOf(com.giftmarket.global.exception.MoneyCalculationException.class);
                assertThat(managedFirst.getStockQuantity()).isEqualTo(1);
            } finally { status.setRollbackOnly(); }
        });
        assertThat(products.findById(first.productId()).orElseThrow().getStockQuantity()).isEqualTo(1);
        assertThat(products.findById(second.productId()).orElseThrow().getStockQuantity()).isEqualTo(1);
        assertThat(orders.count()).isEqualTo(before);
        assertThat(payments.count()).isEqualTo(paymentsBefore);
    }

    @Test
    void strictJsonConfigurationRejectsFractionalKrwBeforeServiceInvocation() {
        assertThatThrownBy(() -> requestJson.readValue(
                "{\"providerPaymentKey\":\"test-key\",\"merchantPaymentId\":\"test-order\",\"amount\":1000.5}",
                PaymentConfirmRequest.class)).isInstanceOf(RuntimeException.class);
    }
    @Autowired com.giftmarket.settlement.repository.SettlementLedgerEntryRepository settlementEntries;
    @Autowired com.giftmarket.order.repository.OrderCancellationItemRepository cancellationItems;
    @Autowired com.giftmarket.payment.service.PaymentCancellationTransactionService fullTransactions;
    @Autowired com.giftmarket.payment.service.PaymentTransactionService approvalTransactions;
    @Autowired com.giftmarket.payment.service.PartialPaymentCancellationTransactionService partialTransactions;
    @Autowired com.giftmarket.payment.service.PartialCancellationReconciliationTransactionService partialReconciliationTransactions;
    @Autowired javax.sql.DataSource batchDataSource;

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
        transactions.executeWithoutResult(status ->
                doThrow(new DataIntegrityViolationException("forced database write failure")).when(ledger).recordInitialSales(any(), any()));
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
    void twoSellerCancellationsDuringRefundDoNotRefundTwice() throws Exception {
        PaidFixture f = paidFixture();
        f.pg().holdFirstCancellation = true;
        var first = new SellerOrderCancelRequest(UUID.randomUUID().toString(), "reason", List.of(new OrderCancellationItemRequest(f.item1(), 2)));
        var second = new SellerOrderCancelRequest(UUID.randomUUID().toString(), "reason", List.of(new OrderCancellationItemRequest(f.item1(), 2)));
        var results = concurrentlyWhileFirstRefundIsPending(f.pg(),
                () -> partialCancellation.createBySeller(f.sellerUserId(), f.sellerOrderId(), first),
                () -> partialCancellation.createBySeller(f.sellerUserId(), f.sellerOrderId(), second));
        assertThat(results.stream().filter(OrderCancellationResponse.class::isInstance)).hasSize(1);
        assertPartialState(f, 2, 0, 20_000L, 1);
        assertThat(f.pg().cancelCalls).isEqualTo(1);
    }

    @Test
    void sellerDuplicateDuringRefundAndCompletedRetryRefundOnlyOnce() throws Exception {
        PaidFixture f = paidFixture();
        var request = new SellerOrderCancelRequest(UUID.randomUUID().toString(), "reason", List.of(new OrderCancellationItemRequest(f.item1(), 1)));
        f.pg().holdFirstCancellation = true;
        var results = concurrentlyWhileFirstRefundIsPending(f.pg(),
                () -> partialCancellation.createBySeller(f.sellerUserId(), f.sellerOrderId(), request),
                () -> partialCancellation.createBySeller(f.sellerUserId(), f.sellerOrderId(), request));
        assertThat(results).allMatch(OrderCancellationResponse.class::isInstance);
        var first = (OrderCancellationResponse) results.getFirst();
        var retry = partialCancellation.createBySeller(f.sellerUserId(), f.sellerOrderId(), request);
        assertThat(retry.cancellationId()).isEqualTo(first.cancellationId());
        assertPartialState(f, 1, 0, 10_000L, 1);
        assertThat(f.pg().cancelCalls).isEqualTo(1);
    }

    @Test
    void sellerAndBuyerDuringRefundCannotRefundSameQuantityTwice() throws Exception {
        PaidFixture f = paidFixture();
        f.pg().holdFirstCancellation = true;
        var seller = new SellerOrderCancelRequest(UUID.randomUUID().toString(), "reason", List.of(new OrderCancellationItemRequest(f.item1(), 2)));
        var buyer = new OrderCancellationCreateRequest(UUID.randomUUID().toString(), f.sellerOrderId(), "reason", List.of(new OrderCancellationItemRequest(f.item1(), 2)));
        var results = concurrentlyWhileFirstRefundIsPending(f.pg(),
                () -> partialCancellation.createBySeller(f.sellerUserId(), f.sellerOrderId(), seller),
                () -> partialCancellation.create(f.buyerId(), f.order().orderId(), buyer));
        assertThat(results.stream().filter(OrderCancellationResponse.class::isInstance)).hasSize(1);
        assertPartialState(f, 2, 0, 20_000L, 1);
        assertThat(f.pg().cancelCalls).isEqualTo(1);
    }

    @Test
    void sellerAndBuyerCannotCancelSameRemainingQuantityConcurrently() throws Exception {
        PaidFixture f = paidFixture();
        var sellerRequest = new SellerOrderCancelRequest(UUID.randomUUID().toString(), "reason", List.of(new OrderCancellationItemRequest(f.item1(), 2)));
        var buyerRequest = new OrderCancellationCreateRequest(UUID.randomUUID().toString(), f.sellerOrderId(), "reason", List.of(new OrderCancellationItemRequest(f.item1(), 2)));
        var results = concurrently(
                () -> sellerManagement.createCancelForExecution(f.sellerUserId(), f.sellerOrderId(), sellerRequest),
                () -> buyerCancellation.create(f.buyerId(), f.order().orderId(), buyerRequest));
        assertThat(results.stream().filter(value -> value instanceof com.giftmarket.order.service.SellerOrderCancellationCreateResult || value instanceof OrderCancellationResponse)).hasSize(1);
        assertThat(cancellations.findAllByOrderIdOrderByRequestedAtDescIdDesc(f.order().orderId())).hasSize(1);
        assertThat(refunds.countByPaymentIdAndStatus(f.order().paymentId(), PaymentCancellationStatus.SUCCEEDED)).isZero();
    }

    @Test
    void sellerCancellationAndShippingCannotBothStart() throws Exception {
        PaidFixture f = paidFixture();
        sellerManagement.prepare(f.sellerUserId(), f.sellerOrderId());
        var request = new SellerOrderCancelRequest(UUID.randomUUID().toString(), "reason", List.of(new OrderCancellationItemRequest(f.item1(), 1)));
        var results = concurrently(
                () -> sellerManagement.createCancelForExecution(f.sellerUserId(), f.sellerOrderId(), request),
                () -> sellerManagement.ship(f.sellerUserId(), f.sellerOrderId(), new SellerOrderShipRequest("carrier", "TRACK-1")));
        assertThat(results.stream().filter(value -> value instanceof com.giftmarket.order.service.SellerOrderCancellationCreateResult || value instanceof com.giftmarket.order.dto.response.SellerOrderDetailResponse)).hasSize(1);
        assertThat(cancellations.findAllByOrderIdOrderByRequestedAtDescIdDesc(f.order().orderId()).size()).isLessThanOrEqualTo(1);
    }

    @Test
    void sellerCancellationSameKeyConcurrentIsIdempotent() throws Exception {
        PaidFixture f = paidFixture();
        String key = UUID.randomUUID().toString();
        var request = new SellerOrderCancelRequest(key, "reason", List.of(new OrderCancellationItemRequest(f.item1(), 1)));
        var results = concurrently(
                () -> sellerManagement.createCancelForExecution(f.sellerUserId(), f.sellerOrderId(), request),
                () -> sellerManagement.createCancelForExecution(f.sellerUserId(), f.sellerOrderId(), request));
        assertThat(results).allMatch(com.giftmarket.order.service.SellerOrderCancellationCreateResult.class::isInstance);
        assertThat(cancellations.findAllByOrderIdOrderByRequestedAtDescIdDesc(f.order().orderId())).hasSize(1);
        assertThat(refunds.countByPaymentIdAndStatus(f.order().paymentId(), PaymentCancellationStatus.SUCCEEDED)).isZero();
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(1);
    }

    @Test
    void sellerCancellationSameKeyDifferentItemsConflicts() throws Exception {
        PaidFixture f = paidFixture();
        String key = UUID.randomUUID().toString();
        var results = concurrently(
                () -> sellerManagement.createCancelForExecution(f.sellerUserId(), f.sellerOrderId(), new SellerOrderCancelRequest(key, "reason", List.of(new OrderCancellationItemRequest(f.item1(), 1)))),
                () -> sellerManagement.createCancelForExecution(f.sellerUserId(), f.sellerOrderId(), new SellerOrderCancelRequest(key, "reason", List.of(new OrderCancellationItemRequest(f.item2(), 1)))));
        assertThat(results.stream().filter(com.giftmarket.order.service.SellerOrderCancellationCreateResult.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(com.giftmarket.seller.exception.SellerException.class::isInstance)).hasSize(1);
        assertThat(cancellations.findAllByOrderIdOrderByRequestedAtDescIdDesc(f.order().orderId())).hasSize(1);
    }

    @Test
    void differentItemsCanBeCanceledAfterPreviousSellerCancellationCompletes() {
        PaidFixture f = paidFixture();
        var first = new SellerOrderCancelRequest(UUID.randomUUID().toString(), "reason", List.of(new OrderCancellationItemRequest(f.item1(), 1)));
        var second = new SellerOrderCancelRequest(UUID.randomUUID().toString(), "reason", List.of(new OrderCancellationItemRequest(f.item2(), 1)));
        assertThat(partialCancellation.createBySeller(f.sellerUserId(), f.sellerOrderId(), first).status()).isEqualTo(OrderCancellationStatus.COMPLETED);
        assertThat(partialCancellation.createBySeller(f.sellerUserId(), f.sellerOrderId(), second).status()).isEqualTo(OrderCancellationStatus.COMPLETED);
        assertPartialState(f, 1, 1, 20_000L, 2);
    }

    @Test
    void twoSellerRequestsForSameItemCannotBothStart() throws Exception {
        PaidFixture f = paidFixture();
        var results = concurrently(
                () -> sellerManagement.createCancelForExecution(f.sellerUserId(), f.sellerOrderId(), new SellerOrderCancelRequest(UUID.randomUUID().toString(), "reason", List.of(new OrderCancellationItemRequest(f.item1(), 2)))),
                () -> sellerManagement.createCancelForExecution(f.sellerUserId(), f.sellerOrderId(), new SellerOrderCancelRequest(UUID.randomUUID().toString(), "reason", List.of(new OrderCancellationItemRequest(f.item1(), 2)))));
        assertThat(results.stream().filter(com.giftmarket.order.service.SellerOrderCancellationCreateResult.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(com.giftmarket.seller.exception.SellerException.class::isInstance)).hasSize(1);
        assertThat(cancellations.findAllByOrderIdOrderByRequestedAtDescIdDesc(f.order().orderId())).hasSize(1);
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
        transactions.executeWithoutResult(status -> doThrow(new DataIntegrityViolationException("forced ledger write failure"))
                .when(cancellationLedger).recordCancellation(any(), any(), any()));
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void lostPartialARecoversAfterBWithoutDuplicateStockQuantityOrLedger(boolean reverseHistory) throws Exception {
        PaidFixture f = paidFixture();
        doAnswer(invocation -> {
            f.pg().cancel(invocation.getArgument(0));
            throw new PaymentGatewayUncertainException("isolated lost A response", null);
        }).when(f.pg().gateway).cancel(any());
        var requestA = partialRequest(f.sellerOrderId(), f.item1(), UUID.randomUUID().toString());
        partialCancellation.create(f.buyerId(), f.order().orderId(), requestA);
        var a = cancellations.findByClientRequestKey(requestA.clientRequestKey()).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(OrderCancellationStatus.PROCESSING);
        doAnswer(invocation -> f.pg().cancel(invocation.getArgument(0))).when(f.pg().gateway).cancel(any());
        partialCancellation.create(f.buyerId(), f.order().orderId(),
                partialRequest(f.sellerOrderId(), f.item2(), UUID.randomUUID().toString()));
        assertThat(orderItems.findById(f.item1()).orElseThrow().getCanceledQuantity()).isZero();
        assertThat(orderItems.findById(f.item2()).orElseThrow().getCanceledQuantity()).isEqualTo(1);
        var query = f.pg().query();
        if (reverseHistory) {
            var reversed = new ArrayList<>(query.cancellations());
            java.util.Collections.reverse(reversed);
            query = withHistory(query, reversed);
        }
        var observed = query;
        assertThat(concurrently(
                () -> { partialReconciliation.reconcileFromWebhook(f.order().paymentId(), observed); return "done"; },
                () -> { partialReconciliation.reconcileFromWebhook(f.order().paymentId(), observed); return "done"; }))
                .containsExactlyInAnyOrder("done", "done");
        partialReconciliation.reconcileFromWebhook(f.order().paymentId(), query);
        assertPartialState(f, 1, 1, 20_000L, 2);
        assertThat(f.pg().cancelCalls).isEqualTo(2);
        assertThat(settlementEntries.findAllBySellerOrderIdOrderByOccurredAtAscIdAsc(f.sellerOrderId()))
                .hasSize(6).filteredOn(e -> e.getType() == com.giftmarket.settlement.entity.SettlementLedgerType.CANCELLATION_REFUND)
                .hasSize(2);
        assertThat(refunds.findByOrderCancellationId(a.getId()).orElseThrow().getProviderTransactionKey())
                .isEqualTo(f.pg().cancellationTransactions.getFirst().providerTransactionId());
    }

    @Test
    void historicalPartialRecoveryRollsBackLedgerFailureAndRetriesOnlyLocalCompletion() {
        PaidFixture f = paidFixture();
        doAnswer(invocation -> {
            f.pg().cancel(invocation.getArgument(0));
            throw new PaymentGatewayUncertainException("isolated lost A response", null);
        }).when(f.pg().gateway).cancel(any());
        var requestA = partialRequest(f.sellerOrderId(), f.item1(), UUID.randomUUID().toString());
        partialCancellation.create(f.buyerId(), f.order().orderId(), requestA);
        doAnswer(invocation -> f.pg().cancel(invocation.getArgument(0))).when(f.pg().gateway).cancel(any());
        partialCancellation.create(f.buyerId(), f.order().orderId(),
                partialRequest(f.sellerOrderId(), f.item2(), UUID.randomUUID().toString()));
        transactions.executeWithoutResult(status -> doAnswer(invocation -> {
            invocation.callRealMethod();
            entityManager.flush();
            throw new DataIntegrityViolationException("isolated historical recovery DB failure");
        }).when(cancellationLedger).recordCancellation(any(), any(), any()));
        try { partialReconciliation.reconcileFromWebhook(f.order().paymentId(), f.pg().query()); }
        finally { reset(cancellationLedger); }
        assertThat(orderItems.findById(f.item1()).orElseThrow().getCanceledQuantity()).isZero();
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(1);
        assertThat(refunds.sumAmountByPaymentIdAndStatus(f.order().paymentId(), PaymentCancellationStatus.SUCCEEDED)).isEqualTo(10_000L);
        assertThat(settlementEntries.findAllBySellerOrderIdOrderByOccurredAtAscIdAsc(f.sellerOrderId())).hasSize(4);
        partialReconciliation.reconcileFromWebhook(f.order().paymentId(), f.pg().query());
        partialReconciliation.reconcileFromWebhook(f.order().paymentId(), f.pg().query());
        assertPartialState(f, 1, 1, 20_000L, 2);
        assertThat(settlementEntries.findAllBySellerOrderIdOrderByOccurredAtAscIdAsc(f.sellerOrderId())).hasSize(6);
        assertThat(f.pg().cancelCalls).isEqualTo(2);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void finalBRefundKeepsLostAPendingRecoverableUntilAllLocalCancellationsComplete(boolean multiSeller) {
        PaidFixture f = paidFixture(multiSeller);
        doAnswer(invocation -> {
            f.pg().cancel(invocation.getArgument(0));
            throw new PaymentGatewayUncertainException("isolated lost A response", null);
        }).when(f.pg().gateway).cancel(any());
        var a = partialCancellation.create(f.buyerId(), f.order().orderId(), new OrderCancellationCreateRequest(
                UUID.randomUUID().toString(), f.sellerOrderId(), "A", List.of(new OrderCancellationItemRequest(f.item1(), 2))));
        var oldQuery = f.pg().query();
        doAnswer(invocation -> f.pg().cancel(invocation.getArgument(0))).when(f.pg().gateway).cancel(any());
        Long secondSellerOrder = transactions.execute(status -> orderItems.findById(f.item2()).orElseThrow().getSellerOrder().getId());
        partialCancellation.create(f.buyerId(), f.order().orderId(), new OrderCancellationCreateRequest(
                UUID.randomUUID().toString(), secondSellerOrder, "B", List.of(new OrderCancellationItemRequest(f.item2(), 2))));
        assertThat(f.pg().refunded).isEqualTo(f.order().totalAmount());
        partialReconciliation.reconcileFromWebhook(f.order().paymentId(), oldQuery);
        assertThat(cancellations.findById(a.cancellationId()).orElseThrow().getStatus()).isEqualTo(OrderCancellationStatus.PROCESSING);
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(1);
        assertThat(payments.findById(f.order().paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PARTIALLY_CANCELED);
        assertThat(orders.findById(f.order().orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        partialReconciliation.reconcileFromWebhook(f.order().paymentId(), f.pg().query());
        partialReconciliation.reconcileFromWebhook(f.order().paymentId(), f.pg().query());
        assertThat(cancellations.findById(a.cancellationId()).orElseThrow().getStatus()).isEqualTo(OrderCancellationStatus.COMPLETED);
        assertThat(payments.findById(f.order().paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(orders.findById(f.order().orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThat(products.findById(f.product2()).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThat(refunds.countByPaymentIdAndStatus(f.order().paymentId(), PaymentCancellationStatus.SUCCEEDED)).isEqualTo(2);
        transactions.executeWithoutResult(status -> {
            for (var sellerOrder : sellerOrders.findAllByOrderIdOrderByIdAsc(f.order().orderId())) {
                assertThat(settlementEntries.findAllBySellerOrderIdOrderByOccurredAtAscIdAsc(sellerOrder.getId()).stream()
                        .mapToLong(com.giftmarket.settlement.entity.SettlementLedgerEntry::getAmount).sum()).isZero();
            }
        });
        assertThat(f.pg().cancelCalls).isEqualTo(2);
    }

    private GatewayPaymentQueryResult withHistory(GatewayPaymentQueryResult query, List<GatewayCancellationTransaction> history) {
        return new GatewayPaymentQueryResult(query.status(), query.providerPaymentKey(), query.providerTransactionId(),
                query.merchantPaymentId(), query.amount(), query.currency(), query.method(), query.easyPayProvider(),
                query.providerStatus(), query.approvedAt(), query.remainingAmount(), query.canceledAt(), query.partialCancelable(), history);
    }

    @Test
    void approvalBatchReachesPaidTailBeyondUnresolvedRowsIncludingAfterRestart() {
        isolateBatchCandidates();
        var created = new ArrayList<OrderCreateResponse>();
        var buyers = new ArrayList<Long>();
        for (int index = 0; index < 4; index++) {
            Fixture f = fixture();
            var order = orderService.createDirectOrder(f.buyer1(), request(f.productId()));
            created.add(order); buyers.add(f.buyer1());
            transactions.executeWithoutResult(status -> payments.findById(order.paymentId()).orElseThrow()
                    .startConfirm("batch-approval-" + order.paymentId(), LocalDateTime.now().minusMinutes(2)));
        }
        var gateway = mock(PaymentGateway.class);
        when(gatewayRegistry.get(PaymentProvider.TOSS)).thenReturn(gateway);
        when(gateway.getPayment(any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            var order = created.stream().filter(value -> key.equals("batch-approval-" + value.paymentId())).findFirst().orElseThrow();
            boolean last = order == created.getLast();
            return new GatewayPaymentQueryResult(last ? GatewayPaymentStatus.PAID : GatewayPaymentStatus.UNKNOWN,
                    key, "batch-approval-tx-" + order.paymentId(), order.merchantPaymentId(), order.totalAmount(), "KRW",
                    PaymentMethod.CARD, null, last ? "DONE" : "UNKNOWN", LocalDateTime.now());
        });
        var properties = smallBatchProperties();
        var service = new com.giftmarket.payment.service.PaymentReconciliationService(payments, approvalTransactions,
                gatewayRegistry, properties, newBatchExecutor());
        service.reconcilePayments();
        assertThat(payments.findById(created.getLast().paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CONFIRMING);
        // A new executor models process restart: old rows are retried, then the tail is reached.
        var restarted = new com.giftmarket.payment.service.PaymentReconciliationService(payments, approvalTransactions,
                gatewayRegistry, properties, newBatchExecutor());
        restarted.reconcilePayments(); restarted.reconcilePayments();
        for (var order : created.subList(0, 3)) {
            assertThat(payments.findById(order.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CONFIRMING);
        }
        assertThat(payments.findById(created.getLast().paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(orders.findById(created.getLast().orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        verify(gateway, never()).confirm(any());
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void fullCancellationBatchSkipsUnresolvedHeadAndRestoresTailOnlyOnce() {
        isolateBatchCandidates();
        var fixtures = new ArrayList<PaidFixture>();
        for (int index = 0; index < 4; index++) {
            PaidFixture f = paidFixture(); fixtures.add(f);
            var start = fullTransactions.start(f.buyerId(), f.order().orderId(), new OrderCancelRequest(UUID.randomUUID().toString(), "batch full"));
            if (index == 3) f.pg().cancel(new GatewayCancelCommand(start.providerPaymentKey(), start.merchantPaymentId(),
                    start.amount(), start.currency(), start.reason(), start.idempotencyKey()));
            transactions.executeWithoutResult(status -> org.springframework.test.util.ReflectionTestUtils.setField(
                    refunds.findById(start.cancellationId()).orElseThrow(), "requestedAt", LocalDateTime.now().minusMinutes(2)));
        }
        var gateway = batchGateway(fixtures);
        var service = new com.giftmarket.payment.service.PaymentCancellationReconciliationService(refunds, fullTransactions,
                gatewayRegistry, smallBatchProperties(), newBatchExecutor());
        service.reconcileCancellations(); service.reconcileCancellations(); service.reconcileCancellations();
        for (var f : fixtures.subList(0, 3)) {
            assertThat(payments.findById(f.order().paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELING);
            assertThat(orderItems.findById(f.item1()).orElseThrow().getCanceledQuantity()).isZero();
        }
        assertFullCancellation(fixtures.getLast(), 1, true);
        assertThat(products.findById(fixtures.getLast().product1()).orElseThrow().getStockQuantity()).isEqualTo(3);
        verify(gateway, never()).cancel(any());
        assertThat(fixtures.getLast().pg().cancelCalls).isEqualTo(1);
    }

    @Test
    void partialBatchReachesTailAfterTimeoutAndPermanentUnresolvedRequests() {
        isolateBatchCandidates();
        var fixtures = new ArrayList<PaidFixture>();
        for (int index = 0; index < 4; index++) {
            PaidFixture f = paidFixture(); fixtures.add(f);
            var cancellation = buyerCancellation.create(f.buyerId(), f.order().orderId(),
                    partialRequest(f.sellerOrderId(), f.item1(), UUID.randomUUID().toString()));
            var start = partialTransactions.start(cancellation.cancellationId());
            if (index == 3) f.pg().cancel(GatewayCancelCommand.partial(start.providerPaymentKey(), start.merchantPaymentId(),
                    start.originalAmount(), start.currency(), start.reason(), start.idempotencyKey(), start.cancelAmount()));
            transactions.executeWithoutResult(status -> org.springframework.test.util.ReflectionTestUtils.setField(
                    refunds.findById(start.paymentCancellationId()).orElseThrow(), "requestedAt", LocalDateTime.now().minusMinutes(2)));
        }
        var gateway = batchGateway(fixtures);
        doThrow(new PaymentGatewayUncertainException("isolated PG timeout", null))
                .when(gateway).getPayment(fixtures.getFirst().pg().key);
        var service = new com.giftmarket.payment.service.PartialPaymentCancellationReconciliationService(refunds,
                partialReconciliationTransactions, partialTransactions, gatewayRegistry, smallBatchProperties(), newBatchExecutor());
        service.reconcilePartialCancellations(); service.reconcilePartialCancellations(); service.reconcilePartialCancellations();
        for (var f : fixtures.subList(0, 3)) {
            assertThat(orderItems.findById(f.item1()).orElseThrow().getCanceledQuantity()).isZero();
            assertThat(refunds.countByPaymentIdAndStatus(f.order().paymentId(), PaymentCancellationStatus.REQUESTED)).isEqualTo(1);
        }
        assertPartialState(fixtures.getLast(), 1, 0, 10_000L, 1);
        assertThat(settlementEntries.findAllBySellerOrderIdOrderByOccurredAtAscIdAsc(fixtures.getLast().sellerOrderId())).hasSize(4);
        verify(gateway, never()).cancel(any());
        assertThat(fixtures.getLast().pg().cancelCalls).isEqualTo(1);
    }

    @Test
    void separateBatchInstancesUseMysqlSessionLockAndReleaseItAfterFailure() throws Exception {
        var first = new com.giftmarket.payment.service.ReconciliationBatchLock(batchDataSource);
        var second = new com.giftmarket.payment.service.ReconciliationBatchLock(batchDataSource);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var count = new java.util.concurrent.atomic.AtomicInteger();
        try (var workers = Executors.newSingleThreadExecutor()) {
            var running = workers.submit(() -> first.run(com.giftmarket.payment.service.ReconciliationBatchExecutor.Kind.APPROVAL, () -> {
                count.incrementAndGet(); entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("isolated lock timeout"); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            }));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                second.run(com.giftmarket.payment.service.ReconciliationBatchExecutor.Kind.APPROVAL, count::incrementAndGet);
                assertThat(count.get()).isEqualTo(1);
            } finally { release.countDown(); }
            running.get(10, TimeUnit.SECONDS);
        }
        assertThatThrownBy(() -> first.run(com.giftmarket.payment.service.ReconciliationBatchExecutor.Kind.APPROVAL,
                () -> { throw new IllegalStateException("isolated work failure"); })).isInstanceOf(IllegalStateException.class);
        second.run(com.giftmarket.payment.service.ReconciliationBatchExecutor.Kind.APPROVAL, count::incrementAndGet);
        assertThat(count.get()).isEqualTo(2);
    }

    private com.giftmarket.payment.service.ReconciliationBatchExecutor newBatchExecutor() {
        return new com.giftmarket.payment.service.ReconciliationBatchExecutor(
                new com.giftmarket.payment.service.ReconciliationBatchLock(batchDataSource));
    }

    private com.giftmarket.payment.config.PaymentProperties smallBatchProperties() {
        var properties = new com.giftmarket.payment.config.PaymentProperties();
        properties.setReconciliationBatchSize(2); properties.setCancelReconciliationBatchSize(2);
        properties.setPartialCancellationReconciliationBatchSize(2);
        properties.setReconciliationDelaySeconds(1); properties.setCancelReconciliationDelaySeconds(1);
        properties.setPartialCancellationReconciliationDelaySeconds(1);
        return properties;
    }

    private void isolateBatchCandidates() {
        // Only this ephemeral test DB is changed. Keep unrelated fixtures outside the due window.
        transactions.executeWithoutResult(status -> {
            entityManager.createQuery("update Payment p set p.confirmingAt = :future where p.status = :status")
                    .setParameter("future", LocalDateTime.now().plusDays(1)).setParameter("status", PaymentStatus.CONFIRMING).executeUpdate();
            entityManager.createQuery("update PaymentCancellation c set c.requestedAt = :future where c.status = :status")
                    .setParameter("future", LocalDateTime.now().plusDays(1)).setParameter("status", PaymentCancellationStatus.REQUESTED).executeUpdate();
        });
    }

    private PaymentGateway batchGateway(List<PaidFixture> fixtures) {
        var gateway = mock(PaymentGateway.class);
        when(gatewayRegistry.get(PaymentProvider.TOSS)).thenReturn(gateway);
        when(gateway.getPayment(any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            var f = fixtures.stream().filter(value -> value.pg().key.equals(key)).findFirst().orElseThrow();
            return f == fixtures.getLast() ? f.pg().query() : new GatewayPaymentQueryResult(GatewayPaymentStatus.UNKNOWN,
                    key, null, f.order().merchantPaymentId(), f.order().totalAmount(), "KRW", null, null, "UNKNOWN", null);
        });
        return gateway;
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

    @Test
    @org.junit.jupiter.api.DisplayName("전체취소 환불·재고·수량·원장 멱등성")
    void paidFullCancellationAndRetryRefundAndRestoreOnlyOnce() {
        PaidFixture f = paidFixture();
        var request = new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소");
        cancellationService.cancel(f.buyerId(), f.order().orderId(), request);
        cancellationService.cancel(f.buyerId(), f.order().orderId(), request);
        assertThat(f.pg().cancelCalls).isEqualTo(1);
        assertThat(f.pg().refunded).isEqualTo(f.order().totalAmount());
        assertFullCancellation(f, 1, true);
        assertThat(orders.findById(f.order().orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThat(products.findById(f.product2()).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThat(refunds.countByPaymentIdAndStatus(f.order().paymentId(), PaymentCancellationStatus.SUCCEEDED)).isEqualTo(1);
        verifyNoInteractions(tossPaymentClient);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = com.giftmarket.order.entity.SellerOrderStatus.class,
            names = {"PREPARING", "SHIPPED", "DELIVERED"})
    void paidFullCancellationRejectsFulfillmentStates(com.giftmarket.order.entity.SellerOrderStatus state) {
        PaidFixture f = paidFixture();
        transactions.executeWithoutResult(status -> {
            var seller = sellerOrders.findById(f.sellerOrderId()).orElseThrow();
            seller.prepare(LocalDateTime.now());
            if (state != com.giftmarket.order.entity.SellerOrderStatus.PREPARING) seller.markShipped(LocalDateTime.now());
            if (state == com.giftmarket.order.entity.SellerOrderStatus.DELIVERED) seller.markDelivered(LocalDateTime.now());
        });
        assertThatThrownBy(() -> cancellationService.cancel(f.buyerId(), f.order().orderId(),
                new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소")))
                .isInstanceOf(com.giftmarket.payment.exception.PaymentException.class);
        assertThat(f.pg().cancelCalls).isZero();
        assertThat(payments.findById(f.order().paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(1);
    }

    @Test void fullCancellationRejectsAnActivePartialCancellation() {
        PaidFixture f = paidFixture();
        buyerCancellation.create(f.buyerId(), f.order().orderId(), partialRequest(f.sellerOrderId(), f.item1(), UUID.randomUUID().toString()));
        assertThatThrownBy(() -> cancellationService.cancel(f.buyerId(), f.order().orderId(),
                new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소")))
                .isInstanceOf(com.giftmarket.payment.exception.PaymentException.class);
        assertThat(f.pg().cancelCalls).isZero();
    }

    @Test void duplicatePaidFullCancellationWhileProviderIsPendingRestoresOnlyOnce() throws Exception {
        PaidFixture f = paidFixture();
        f.pg().holdFirstCancellation = true;
        var request = new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소");
        var results = concurrentlyWhileFirstRefundIsPending(f.pg(),
                () -> cancellationService.cancel(f.buyerId(), f.order().orderId(), request),
                () -> cancellationService.cancel(f.buyerId(), f.order().orderId(), request));
        assertThat(results).allMatch(com.giftmarket.order.dto.response.OrderCancelResponse.class::isInstance);
        assertThat(f.pg().results).hasSize(1);
        assertThat(f.pg().refunded).isEqualTo(f.order().totalAmount());
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThat(refunds.countByPaymentIdAndStatus(f.order().paymentId(), PaymentCancellationStatus.SUCCEEDED)).isEqualTo(1);
    }

    @Test void onePreparingSellerBlocksFullCancellationOfMultiSellerOrder() {
        PaidFixture f = paidFixture(true);
        var all = sellerOrders.findAllByOrderIdOrderByIdAsc(f.order().orderId());
        assertThat(all).hasSize(2);
        transactions.executeWithoutResult(status -> sellerOrders.findById(all.getLast().getId())
                .orElseThrow().prepare(LocalDateTime.now()));
        assertThatThrownBy(() -> cancellationService.cancel(f.buyerId(), f.order().orderId(),
                new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소")))
                .isInstanceOf(com.giftmarket.payment.exception.PaymentException.class);
        assertThat(f.pg().cancelCalls).isZero();
    }

    @Test void fullCancellationAndPreparationCannotBothStart() throws Exception {
        PaidFixture f = paidFixture();
        var results = concurrently(
                () -> cancellationService.cancel(f.buyerId(), f.order().orderId(), new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소")),
                () -> sellerManagement.prepare(f.sellerUserId(), f.sellerOrderId()));
        assertThat(results.stream().filter(Throwable.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(Throwable.class::isInstance)).allMatch(value ->
                value instanceof com.giftmarket.payment.exception.PaymentException
                        || value instanceof com.giftmarket.seller.exception.SellerException);
        if (f.pg().cancelCalls == 1) {
            assertThat(orders.findById(f.order().orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        } else {
            assertThat(sellerOrders.findById(f.sellerOrderId()).orElseThrow().getStatus())
                    .isEqualTo(com.giftmarket.order.entity.SellerOrderStatus.PREPARING);
        }
    }

    @Test void pendingFullRefundBlocksSellerPreparationShipmentAndNewCancellation() throws Exception {
        PaidFixture f = paidFixture();
        f.pg().holdFirstCancellation = true;
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> cancellationService.cancel(f.buyerId(), f.order().orderId(),
                    new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소")));
            try {
                assertThat(f.pg().cancelEntered.await(20, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> sellerManagement.prepare(f.sellerUserId(), f.sellerOrderId()))
                        .isInstanceOf(com.giftmarket.seller.exception.SellerException.class);
                assertThatThrownBy(() -> sellerManagement.ship(f.sellerUserId(), f.sellerOrderId(), new SellerOrderShipRequest("택배", "TEST-123")))
                        .isInstanceOf(com.giftmarket.seller.exception.SellerException.class);
                assertThatThrownBy(() -> partialCancellation.createBySeller(f.sellerUserId(), f.sellerOrderId(),
                        new SellerOrderCancelRequest(UUID.randomUUID().toString(), "판매자 취소", null)))
                        .isInstanceOf(com.giftmarket.seller.exception.SellerException.class);
                assertThat(sellerOrders.findById(f.sellerOrderId()).orElseThrow().getStatus())
                        .isEqualTo(com.giftmarket.order.entity.SellerOrderStatus.PAID);
            } finally {
                f.pg().releaseCancel.countDown();
            }
            future.get(20, TimeUnit.SECONDS);
        }
    }

    @Test void fullCancellationCannotRacePastPreparingIntoShipment() throws Exception {
        PaidFixture f = paidFixture();
        sellerManagement.prepare(f.sellerUserId(), f.sellerOrderId());
        var results = concurrently(
                () -> cancellationService.cancel(f.buyerId(), f.order().orderId(), new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소")),
                () -> sellerManagement.ship(f.sellerUserId(), f.sellerOrderId(), new SellerOrderShipRequest("택배", "RACE-123")));
        assertThat(results.stream().filter(Throwable.class::isInstance)).hasSize(1)
                .allMatch(com.giftmarket.payment.exception.PaymentException.class::isInstance);
        assertThat(sellerOrders.findById(f.sellerOrderId()).orElseThrow().getStatus())
                .isEqualTo(com.giftmarket.order.entity.SellerOrderStatus.SHIPPED);
        assertThat(f.pg().cancelCalls).isZero();
    }

    @Test void fullCancellationCannotRefundConfirmedDeliveredItems() {
        PaidFixture f = paidFixture();
        sellerManagement.prepare(f.sellerUserId(), f.sellerOrderId());
        sellerManagement.ship(f.sellerUserId(), f.sellerOrderId(), new SellerOrderShipRequest("택배", "CONFIRMED-123"));
        sellerManagement.deliver(f.sellerUserId(), f.sellerOrderId());
        purchaseConfirmation.confirm(f.buyerId(), f.order().orderId(), f.item1());
        assertThat(orderItems.findById(f.item1()).orElseThrow().getConfirmedQuantity()).isEqualTo(2);
        assertThatThrownBy(() -> cancellationService.cancel(f.buyerId(), f.order().orderId(),
                new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소")))
                .isInstanceOf(com.giftmarket.payment.exception.PaymentException.class);
        assertThat(f.pg().cancelCalls).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void fullCancellationAllocatesSnapshotsAndCommissionPerSeller(boolean multiSeller) {
        PaidFixture f = paidFixture(multiSeller, true);
        // Catalog changes must not affect the paid order snapshots or commission snapshot.
        transactions.executeWithoutResult(status -> {
            var product = products.findById(f.product1()).orElseThrow();
            org.springframework.test.util.ReflectionTestUtils.setField(product, "price", 999_999L);
            org.springframework.test.util.ReflectionTestUtils.setField(product, "shippingFee", 99_999L);
        });
        cancellationService.cancel(f.buyerId(), f.order().orderId(), new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소"));
        assertFullCancellation(f, multiSeller ? 2 : 1, true);
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(6);
        assertThat(products.findById(f.product2()).orElseThrow().getStockQuantity()).isEqualTo(3);
        transactions.executeWithoutResult(status -> {
            var items = orderItems.findAllByOrderIdOrderByIdAsc(f.order().orderId());
            assertThat(items).hasSize(3);
            assertThat(items.stream().filter(item -> item.getVariant() != null)).hasSize(2);
            for (var item : items) {
                if (item.getVariant() != null) assertThat(item.getVariant().getStockQuantity()).isEqualTo(3);
            }
        });
        assertThat(f.pg().refunded).isEqualTo(65_000L); // 22,000 + 12,000 + 20,000 + 3,000 + 3,000 + 5,000
    }

    @Test
    void fullCancellationBeforeInitialLedgerDoesNotCreateNegativeOrphanEntries() {
        transactions.executeWithoutResult(status -> doNothing().when(ledger).recordInitialSales(any(), any()));
        PaidFixture f;
        try { f = paidFixture(true); } finally { reset(ledger); }
        cancellationService.cancel(f.buyerId(), f.order().orderId(), new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소"));
        assertFullCancellation(f, 2, false);
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThat(products.findById(f.product2()).orElseThrow().getStockQuantity()).isEqualTo(3);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void lostFullRefundResponseRecoversByQueryWithoutSecondRefund(boolean reconciliation) {
        PaidFixture f = paidFixture(true);
        doAnswer(invocation -> {
            f.pg().cancel(invocation.getArgument(0));
            throw new PaymentGatewayUncertainException("isolated lost response", null);
        }).when(f.pg().gateway).cancel(any());
        var request = new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소");
        assertThatThrownBy(() -> cancellationService.cancel(f.buyerId(), f.order().orderId(), request))
                .isInstanceOf(com.giftmarket.payment.exception.PaymentException.class);
        assertFullCancellationPending(f);
        if (reconciliation) {
            var start = fullTransactions.startReconciliation(f.order().paymentId(), LocalDateTime.now().plusMinutes(1));
            assertThat(start.action()).isEqualTo(com.giftmarket.payment.service.PaymentCancellationReconciliationStart.Action.QUERY);
            fullTransactions.completeFromReconciliationQuery(start.paymentId(), start.cancellationId(), f.pg().query());
            fullTransactions.completeFromReconciliationQuery(start.paymentId(), start.cancellationId(), f.pg().query());
        } else {
            cancellationService.cancel(f.buyerId(), f.order().orderId(), request);
        }
        assertFullCancellation(f, 2, true);
        assertThat(f.pg().cancelCalls).isEqualTo(1);
    }

    @Test
    void fullRefundDatabaseFailureRollsBackAllSellersAndRecoversExactlyOnce() {
        PaidFixture f = paidFixture(true, true);
        var count = new java.util.concurrent.atomic.AtomicInteger();
        transactions.executeWithoutResult(status -> doAnswer(invocation -> {
            invocation.callRealMethod();
            entityManager.flush();
            if (count.incrementAndGet() == 2) throw new DataIntegrityViolationException("isolated failure after second seller ledger");
            return null;
        }).when(cancellationLedger).recordFullCancellation(any(), any(), any(), anyLong()));
        var request = new OrderCancelRequest(UUID.randomUUID().toString(), "전체취소");
        try {
            assertThatThrownBy(() -> cancellationService.cancel(f.buyerId(), f.order().orderId(), request))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally { reset(cancellationLedger); }
        assertThat(count.get()).isEqualTo(2);
        assertFullCancellationPending(f);
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThat(products.findById(f.product2()).orElseThrow().getStockQuantity()).isEqualTo(1);
        var refund = refunds.findFirstByPaymentIdAndStatusOrderByIdDesc(f.order().paymentId(), PaymentCancellationStatus.REQUESTED).orElseThrow();
        fullTransactions.completeFromReconciliationQuery(f.order().paymentId(), refund.getId(), f.pg().query());
        cancellationService.cancel(f.buyerId(), f.order().orderId(), request);
        assertFullCancellation(f, 2, true);
        assertThat(f.pg().cancelCalls).isEqualTo(1);
        assertThat(products.findById(f.product1()).orElseThrow().getStockQuantity()).isEqualTo(6);
        assertThat(products.findById(f.product2()).orElseThrow().getStockQuantity()).isEqualTo(3);
    }

    private void assertFullCancellationPending(PaidFixture f) {
        assertThat(payments.findById(f.order().paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELING);
        assertThat(orders.findById(f.order().orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(cancellations.countByOrderId(f.order().orderId())).isZero();
        for (var item : orderItems.findAllByOrderIdOrderByIdAsc(f.order().orderId())) assertThat(item.getCanceledQuantity()).isZero();
        for (var sellerOrder : sellerOrders.findAllByOrderIdOrderByIdAsc(f.order().orderId())) {
            assertThat(settlementEntries.findAllBySellerOrderIdOrderByOccurredAtAscIdAsc(sellerOrder.getId()))
                    .noneMatch(entry -> entry.getSourceType() == com.giftmarket.settlement.entity.SettlementLedgerSourceType.PAYMENT_CANCELLATION);
        }
    }

    private void assertFullCancellation(PaidFixture f, int sellerCount, boolean hasInitialLedger) {
        transactions.executeWithoutResult(status -> {
            assertThat(payments.findById(f.order().paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(orders.findById(f.order().orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
            var succeeded = refunds.findAll().stream().filter(value -> value.getPayment().getId().equals(f.order().paymentId())
                    && value.getStatus() == PaymentCancellationStatus.SUCCEEDED).toList();
            assertThat(succeeded).hasSize(1);
            var histories = cancellations.findAllByOrderIdOrderByRequestedAtDescIdDesc(f.order().orderId());
            assertThat(histories).hasSize(sellerCount).allMatch(c -> c.getStatus() == OrderCancellationStatus.COMPLETED);
            long totalRefund = 0;
            for (var history : histories) {
                var items = orderItems.findAllBySellerOrderIdForUpdate(history.getSellerOrder().getId());
                var snapshots = cancellationItems.findAllByOrderCancellationIdOrderByIdAsc(history.getId());
                assertThat(snapshots).hasSize(items.size());
                for (var item : items) {
                    assertThat(item.getCanceledQuantity()).isEqualTo(item.getQuantity());
                    assertThat(snapshots).anyMatch(snapshot -> snapshot.getOrderItem().getId().equals(item.getId())
                            && snapshot.getQuantity() == item.getQuantity());
                }
                long product = items.stream().mapToLong(item -> item.getUnitPrice() * item.getQuantity()).sum();
                long shipping = items.stream().mapToLong(OrderItem::getShippingFee).sum();
                totalRefund += product + shipping;
                var entries = settlementEntries.findAllBySellerOrderIdOrderByOccurredAtAscIdAsc(history.getSellerOrder().getId());
                if (!hasInitialLedger) { assertThat(entries).isEmpty(); continue; }
                assertThat(entries).hasSize(shipping > 0 ? 5 : 4);
                var refundEntry = entries.stream().filter(e -> e.getType() == com.giftmarket.settlement.entity.SettlementLedgerType.CANCELLATION_REFUND).findFirst().orElseThrow();
                var reversal = entries.stream().filter(e -> e.getType() == com.giftmarket.settlement.entity.SettlementLedgerType.COMMISSION_REVERSAL).findFirst().orElseThrow();
                assertThat(refundEntry.getAmount()).isEqualTo(-(product + shipping));
                assertThat(refundEntry.getSourceId()).isEqualTo(succeeded.getFirst().getId());
                assertThat(reversal.getAmount()).isEqualTo(product / 10);
                assertThat(reversal.getCommissionBaseAmount()).isEqualTo(product);
                assertThat(entries).allMatch(e -> e.getEligibleAt().equals(history.getCompletedAt()));
                assertThat(entries.stream().mapToLong(com.giftmarket.settlement.entity.SettlementLedgerEntry::getAmount).sum()).isZero();
            }
            assertThat(totalRefund).isEqualTo(f.order().totalAmount());
        });
        verifyNoInteractions(tossPaymentClient);
    }

    private PaidFixture paidFixture() { return paidFixture(false); }

    private PaidFixture paidFixture(boolean multiSeller) {
        return paidFixture(multiSeller, false);
    }

    private PaidFixture paidFixture(boolean multiSeller, boolean detailed) {
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
            Seller secondSeller = seller;
            if (multiSeller) {
                User secondOwner = User.createOAuthUser("other-" + unique + "@example.test", "다른 판매자", null, AuthProvider.GOOGLE, "other-" + unique);
                entityManager.persist(secondOwner);
                secondSeller = Seller.create(secondOwner, "다른 상점", null);
                entityManager.persist(secondSeller);
            }
            Product second = Product.createDraft(secondSeller, category, "상품2", "브랜드", "요약", "설명", 10_000L, 3, null, true, 0L, 1, 3_000L, 6_000L);
            first.startSale(); second.startSale();
            entityManager.persist(first); entityManager.persist(second);
            com.giftmarket.product.entity.ProductVariant variant1 = null;
            com.giftmarket.product.entity.ProductVariant variant2 = null;
            if (detailed) {
                variant1 = com.giftmarket.product.entity.ProductVariant.create(first, "sku1-" + unique, "색상:흰색", 1_000L, 3);
                variant2 = com.giftmarket.product.entity.ProductVariant.create(first, "sku2-" + unique, "색상:회색", 2_000L, 3);
                entityManager.persist(variant1); entityManager.persist(variant2);
                var group = com.giftmarket.product.entity.ProductOptionGroup.create(first, "색상", 1);
                entityManager.persist(group);
                var white = com.giftmarket.product.entity.ProductOptionValue.create(group, "흰색", 1);
                var gray = com.giftmarket.product.entity.ProductOptionValue.create(group, "회색", 2);
                entityManager.persist(white); entityManager.persist(gray);
                entityManager.persist(com.giftmarket.product.entity.ProductVariantOptionValue.create(variant1, white));
                entityManager.persist(com.giftmarket.product.entity.ProductVariantOptionValue.create(variant2, gray));
                first.changeStockQuantity(6);
                org.springframework.test.util.ReflectionTestUtils.setField(first, "freeShipping", false);
                org.springframework.test.util.ReflectionTestUtils.setField(first, "shippingFee", 3_000L);
                org.springframework.test.util.ReflectionTestUtils.setField(second, "freeShipping", false);
                org.springframework.test.util.ReflectionTestUtils.setField(second, "shippingFee", 5_000L);
            }
            CartItem cart1 = CartItem.create(buyer, first, variant1, 2);
            CartItem cart2 = CartItem.create(buyer, second, null, 2);
            entityManager.persist(cart1); entityManager.persist(cart2);
            CartItem cart3 = detailed ? CartItem.create(buyer, first, variant2, 1) : null;
            if (cart3 != null) entityManager.persist(cart3);
            entityManager.flush();
            return new long[]{buyer.getId(), first.getId(), second.getId(), cart1.getId(), cart2.getId(), owner.getId(),
                    cart3 == null ? 0L : cart3.getId()};
        });
        OrderCreateResponse order = orderService.createOrder(ids[0], new OrderCreateRequest(UUID.randomUUID().toString(),
                detailed ? List.of(ids[3], ids[4], ids[6]) : List.of(ids[3], ids[4]), "구매자", "010-1234-5678", "12345", "서울", null));
        FakePg pg = new FakePg("pg-" + unique, order);
        when(gatewayRegistry.get(PaymentProvider.TOSS)).thenReturn(pg.gateway);
        paymentService.confirm(ids[0], order.paymentId(), new PaymentConfirmRequest(pg.key, order.merchantPaymentId(), order.totalAmount()));
        var items = orderItems.findAllByOrderIdOrderByIdAsc(order.orderId());
        Long firstItem = items.stream().filter(item -> item.getProduct().getId().equals(ids[1])).map(OrderItem::getId).findFirst().orElseThrow();
        Long secondItem = items.stream().filter(item -> item.getProduct().getId().equals(ids[2])).map(OrderItem::getId).findFirst().orElseThrow();
        Long sellerOrder = sellerOrders.findAllByOrderIdOrderByIdAsc(order.orderId()).getFirst().getId();
        return new PaidFixture(ids[0], ids[1], ids[2], firstItem, secondItem, sellerOrder, order, pg, ids[5]);
    }

    private record PaidFixture(Long buyerId, Long product1, Long product2, Long item1, Long item2,
                               Long sellerOrderId, OrderCreateResponse order, FakePg pg, Long sellerUserId) { }

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
            boolean canceled = refunded == order.totalAmount();
            var last = cancellationTransactions.isEmpty() ? null : cancellationTransactions.getLast();
            return new GatewayPaymentQueryResult(canceled ? GatewayPaymentStatus.CANCELED
                    : refunded == 0 ? GatewayPaymentStatus.PAID : GatewayPaymentStatus.PARTIALLY_CANCELED,
                    key, last == null ? "approval" : last.providerTransactionId(), order.merchantPaymentId(), order.totalAmount(), "KRW", PaymentMethod.CARD, null,
                    canceled ? "CANCELED" : "DONE", LocalDateTime.now(), order.totalAmount() - refunded,
                    last == null ? null : last.canceledAt(), true, List.copyOf(cancellationTransactions));
        }
        synchronized GatewayCancelResult cancel(GatewayCancelCommand command) {
            cancelCalls++;
            if (results.containsKey(command.idempotencyKey())) return results.get(command.idempotencyKey());
            long canceledAmount = command.cancelAmount() == null ? command.amount() : command.cancelAmount();
            refunded += canceledAmount;
            long remaining = command.amount() - refunded;
            String tx = "refund-" + UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now().plusSeconds(1);
            var result = new GatewayCancelResult(remaining == 0 ? GatewayPaymentStatus.CANCELED : GatewayPaymentStatus.PARTIALLY_CANCELED, key, tx, order.merchantPaymentId(),
                    order.totalAmount(), remaining, "KRW", remaining == 0 ? "CANCELED" : "PARTIAL_CANCELED", now, canceledAmount, "DONE", remaining);
            cancellationTransactions.add(new GatewayCancellationTransaction(tx, canceledAmount, command.reason(), "DONE", now, remaining));
            results.put(command.idempotencyKey(), result);
            return result;
        }
    }

    @Test
    void concurrentReturnsAllowOnlyOneClaim() throws Exception { assertClaimRace(false, false); }

    @Test
    void concurrentExchangesAllowOnlyOneClaim() throws Exception { assertClaimRace(true, true); }

    @Test
    void concurrentReturnAndExchangeAllowOnlyOneClaim() throws Exception { assertClaimRace(false, true); }

    private void assertClaimRace(boolean firstExchange, boolean secondExchange) throws Exception {
        ClaimFixture f = claimFixture();
        List<Object> results = concurrently(() -> claim(f, firstExchange), () -> claim(f, secondExchange));
        assertThat(results.stream().filter(value -> value instanceof ReturnRequestResponse || value instanceof ExchangeRequestResponse)).hasSize(1);
        assertThat(results.stream().filter(OrderException.class::isInstance)).hasSize(1);
        assertThat(returnRepository.findAllByOrderIdOrderByRequestedAtDescIdDesc(f.orderId()).size() + exchangeRepository.findAllByOrderIdOrderByRequestedAtDescIdDesc(f.orderId()).size()).isEqualTo(1);
        assertThatThrownBy(() -> claim(f, !firstExchange)).isInstanceOf(OrderException.class);
        assertThat(orderItems.findById(f.itemId()).orElseThrow().getReturnedQuantity()).isZero();
        assertThat(products.findById(f.productId()).orElseThrow().getStockQuantity()).isEqualTo(1);
    }

    @Test
    void missingRequiredCollectionAddressRejectsBothClaimsAndOptionalDetailIsAccepted() {
        for (String field : List.of("recipientName", "recipientPhone", "postalCode", "address")) {
            for (String value : new String[] {"", "   "}) {
                ClaimFixture f = claimFixture();
                transactions.executeWithoutResult(status -> { var order = orders.findById(f.orderId()).orElseThrow(); org.springframework.test.util.ReflectionTestUtils.setField(order, field, value); });
                assertThatThrownBy(() -> claim(f, false)).isInstanceOf(OrderException.class);
                assertThatThrownBy(() -> claim(f, true)).isInstanceOf(OrderException.class);
                assertThat(returnRepository.findAllByOrderIdOrderByRequestedAtDescIdDesc(f.orderId())).isEmpty();
                assertThat(exchangeRepository.findAllByOrderIdOrderByRequestedAtDescIdDesc(f.orderId())).isEmpty();
                assertThat(products.findById(f.productId()).orElseThrow().getStockQuantity()).isEqualTo(1);
            }
        }
        for (String detail : new String[] {null, "", "   "}) {
            ClaimFixture f = claimFixture();
            transactions.executeWithoutResult(status -> { var order = orders.findById(f.orderId()).orElseThrow(); org.springframework.test.util.ReflectionTestUtils.setField(order, "addressDetail", detail); });
            assertThat(claim(f, false)).isInstanceOf(ReturnRequestResponse.class);
            ClaimFixture other = claimFixture();
            transactions.executeWithoutResult(status -> { var order = orders.findById(other.orderId()).orElseThrow(); org.springframework.test.util.ReflectionTestUtils.setField(order, "addressDetail", detail); });
            assertThat(claim(other, true)).isInstanceOf(ExchangeRequestResponse.class);
        }
    }

    @Test
    void claimForAnotherOrderItemRemainsAllowed() {
        ClaimFixture f = claimFixture();
        Long secondItemId = transactions.execute(status -> {
            var order = orders.findById(f.orderId()).orElseThrow();
            var sellerOrder = sellerOrders.findById(f.sellerOrderId()).orElseThrow();
            var original = products.findById(f.productId()).orElseThrow();
            var secondProduct = Product.createDraft(original.getSeller(), original.getCategory(), "second product", "brand", "summary", "description", 10_000L, 1, null, true, 0L, 1, 3_000L, 6_000L);
            secondProduct.startSale(); entityManager.persist(secondProduct);
            OrderItem item = OrderItem.create(order, secondProduct, null, original.getSeller(), sellerOrder, null, "second product", null, "store", null, null, 10_000L, 0L, 1, true, 0L, 3_000L, 6_000L);
            entityManager.persist(item); entityManager.flush(); return item.getId();
        });
        assertThat(claim(f, false)).isInstanceOf(ReturnRequestResponse.class);
        assertThat(claim(new ClaimFixture(f.buyerId(), f.orderId(), f.sellerOrderId(), secondItemId, f.productId()), true)).isInstanceOf(ExchangeRequestResponse.class);
    }

    @Test
    void claimStoresFullLongOrderAddresses() {
        for (int length : new int[] {256, 500}) {
            for (boolean exchange : new boolean[] {false, true}) {
                ClaimFixture f = claimFixture();
                String address = "a".repeat(length);
                String detail = "b".repeat(length);
                transactions.executeWithoutResult(status -> { var order = orders.findById(f.orderId()).orElseThrow(); org.springframework.test.util.ReflectionTestUtils.setField(order, "address", address); org.springframework.test.util.ReflectionTestUtils.setField(order, "addressDetail", detail); });
                Object response = claim(f, exchange);
                if (response instanceof ReturnRequestResponse result) { assertThat(result.collectionAddress()).isEqualTo(address); assertThat(result.collectionAddressDetail()).isEqualTo(detail); }
                else { ExchangeRequestResponse result = (ExchangeRequestResponse) response; assertThat(result.collectionAddress()).isEqualTo(address); assertThat(result.collectionAddressDetail()).isEqualTo(detail); }
            }
        }
    }

    private Object claim(ClaimFixture f, boolean exchange) {
        if (exchange) return exchangeRequests.create(f.buyerId(), f.orderId(), f.sellerOrderId(), new ExchangeRequestCreateRequest(
                UUID.randomUUID().toString(), ExchangeReasonType.DEFECTIVE, "reason", "tampered", "tampered", "tampered", "tampered", null,
                "recipient", "010-1234-5678", "12345", "Seoul", null, List.of(new ExchangeRequestItemRequest(f.itemId(), 1, null)), List.of()));
        return returnRequests.create(f.buyerId(), f.orderId(), f.sellerOrderId(), new ReturnRequestCreateRequest(
                UUID.randomUUID().toString(), ReturnReasonType.DEFECTIVE, "reason", "tampered", "tampered", "tampered", "tampered", null, List.of(new ReturnRequestItemRequest(f.itemId(), 1)), List.of()));
    }

    private ClaimFixture claimFixture() {
        Fixture f = fixture();
        OrderCreateResponse created = orderService.createDirectOrder(f.buyer1(), request(f.productId()));
        return transactions.execute(status -> {
            var order = orders.findById(created.orderId()).orElseThrow();
            order.markPaid(LocalDateTime.now().minusDays(2));
            var sellerOrder = sellerOrders.findAllByOrderIdOrderByIdAsc(order.getId()).getFirst();
            sellerOrder.markPaid(); sellerOrder.prepare(LocalDateTime.now().minusDays(2)); sellerOrder.markShipped(LocalDateTime.now().minusDays(1)); sellerOrder.markDelivered(LocalDateTime.now());
            Shipment shipment = Shipment.createShipped(sellerOrder, ShipmentType.ORIGINAL_OUTBOUND, "test", "test", LocalDateTime.now().minusDays(1));
            shipment.deliver(LocalDateTime.now()); entityManager.persist(shipment);
            products.findById(f.productId()).orElseThrow().increaseStock(1);
            var item = orderItems.findAllByOrderIdOrderByIdAsc(order.getId()).getFirst();
            return new ClaimFixture(f.buyer1(), order.getId(), sellerOrder.getId(), item.getId(), f.productId());
        });
    }
    private record ClaimFixture(Long buyerId, Long orderId, Long sellerOrderId, Long itemId, Long productId) {}

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
