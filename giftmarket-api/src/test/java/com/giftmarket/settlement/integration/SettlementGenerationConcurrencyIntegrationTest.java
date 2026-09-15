package com.giftmarket.settlement.integration;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerSourceType;
import com.giftmarket.settlement.entity.SettlementLedgerType;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import com.giftmarket.settlement.repository.SettlementRepository;
import com.giftmarket.settlement.service.SettlementGenerationCommand;
import com.giftmarket.settlement.service.SettlementGenerationService;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:settlement-generation-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false",
        "app.jwt.secret=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "app.jwt.refresh-token-encryption-key=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "payment.toss.secret-key=test-only-key"
})
class SettlementGenerationConcurrencyIntegrationTest {

    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime PERIOD_END = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 9, 30, 23, 59);

    @Autowired SettlementGenerationService generationService;
    @Autowired SettlementRepository settlementRepository;
    @Autowired SettlementLedgerEntryRepository ledgerRepository;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactionTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private Long sellerId;
    private Long ledgerId;
    private Long nullEligibleLedgerId;
    private Long futureLedgerId;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            User buyer = User.createOAuthUser(
                    "settlement-concurrency-buyer@example.com", "buyer", null,
                    AuthProvider.GOOGLE, "settlement-concurrency-buyer"
            );
            entityManager.persist(buyer);
            User sellerUser = User.createOAuthUser(
                    "settlement-concurrency-seller@example.com", "seller", null,
                    AuthProvider.GOOGLE, "settlement-concurrency-seller"
            );
            entityManager.persist(sellerUser);
            Seller seller = Seller.create(sellerUser, "concurrency-store", null);
            entityManager.persist(seller);
            Order order = Order.createPendingPayment(
                    "ORDER-SETTLEMENT-CONCURRENCY", buyer, 10_000L, 0L,
                    "buyer", "01012345678", "12345", "address", null
            );
            entityManager.persist(order);
            SellerOrder sellerOrder = SellerOrder.createPendingPayment(order, seller);
            entityManager.persist(sellerOrder);
            entityManager.flush();
            SettlementLedgerEntry ledger = SettlementLedgerEntry.create(
                    seller,
                    sellerOrder,
                    SettlementLedgerType.SALE_PRODUCT,
                    10_000L,
                    SettlementLedgerSourceType.SELLER_ORDER,
                    sellerOrder.getId(),
                    "SALE_PRODUCT",
                    LocalDateTime.of(2026, 8, 20, 12, 0),
                    LocalDateTime.of(2026, 8, 27, 12, 0),
                    1_000,
                    10_000L,
                    null,
                    null,
                    null
            );
            entityManager.persist(ledger);
            SettlementLedgerEntry nullEligible = ledger(
                    seller,
                    sellerOrder,
                    "SALE_PRODUCT_NULL_ELIGIBLE",
                    LocalDateTime.of(2026, 9, 1, 12, 0),
                    null
            );
            entityManager.persist(nullEligible);
            SettlementLedgerEntry future = ledger(
                    seller,
                    sellerOrder,
                    "SALE_PRODUCT_FUTURE",
                    LocalDateTime.of(2026, 9, 20, 12, 0),
                    LocalDateTime.of(2026, 10, 2, 12, 0)
            );
            entityManager.persist(future);
            entityManager.flush();
            sellerId = seller.getId();
            ledgerId = ledger.getId();
            nullEligibleLedgerId = nullEligible.getId();
            futureLedgerId = future.getId();
        });
    }

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void concurrentGenerateCreatesOneSettlementAndAssignsLedgerOnce() throws Exception {
        SettlementGenerationCommand command = new SettlementGenerationCommand(
                sellerId, PERIOD_START, PERIOD_END, CUTOFF
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Object>> futures = List.of(
                executor.submit(() -> invoke(command, ready, start)),
                executor.submit(() -> invoke(command, ready, start))
        );
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<Object> results = List.of(
                futures.get(0).get(15, TimeUnit.SECONDS),
                futures.get(1).get(15, TimeUnit.SECONDS)
        );

        assertThat(results.stream().filter(result -> result instanceof Optional<?> optional
                && optional.isPresent())).hasSize(1);
        assertThat(results.stream().filter(SettlementException.class::isInstance)).hasSize(1);
        assertThat(settlementRepository.count()).isEqualTo(1L);
        assertThat(ledgerRepository.findById(ledgerId).orElseThrow().getSettlement()).isNotNull();
        assertThat(ledgerRepository.findById(nullEligibleLedgerId).orElseThrow().getSettlement()).isNull();
        assertThat(ledgerRepository.findById(futureLedgerId).orElseThrow().getSettlement()).isNull();
    }

    private SettlementLedgerEntry ledger(
            Seller seller,
            SellerOrder sellerOrder,
            String detailKey,
            LocalDateTime occurredAt,
            LocalDateTime eligibleAt
    ) {
        return SettlementLedgerEntry.create(
                seller,
                sellerOrder,
                SettlementLedgerType.SALE_PRODUCT,
                10_000L,
                SettlementLedgerSourceType.SELLER_ORDER,
                sellerOrder.getId(),
                detailKey,
                occurredAt,
                eligibleAt,
                1_000,
                10_000L,
                null,
                null,
                null
        );
    }

    private Object invoke(
            SettlementGenerationCommand command,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
            return generationService.generate(command);
        } catch (Throwable throwable) {
            return throwable;
        }
    }
}
