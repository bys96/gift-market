package com.giftmarket.product.service;

import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.order.entity.*;
import com.giftmarket.payment.entity.*;
import com.giftmarket.product.entity.*;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.*;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

/** Real Hibernate DDL and real entities, not the reduced SQL failure fixtures. */
@DataJpaTest(showSql = false, properties = {
        "spring.config.location=optional:classpath:/default-variant-migration-test.properties",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("mysql-container")
@Testcontainers
class DefaultVariantJpaMigrationMySqlTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @Autowired DataSource dataSource;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactions;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Test
    void bothSqlFilesRunOnJpaSchemaAndPreserveAllExistingColumnsExceptAllowedForeignKeys() throws Exception {
        fixture();
        try (var c = dataSource.getConnection()) {
            String checks = Files.readString(Path.of("../docs/sql/product-default-variant-checks.sql"));
            runChecks(c, checks);
            Map<String, List<List<String>>> before = new LinkedHashMap<>();
            for (String table : List.of("products", "product_variants", "orders", "seller_orders", "order_items",
                    "cart_items", "exchange_requests", "exchange_request_items", "return_requests", "return_request_items",
                    "payments", "payment_cancellations", "order_cancellations", "order_cancellation_items")) {
                before.put(table, snapshot(c, table));
            }
            migrate(c);
            runChecks(c, checks);
            for (var entry : before.entrySet()) {
                var after = snapshot(c, entry.getKey());
                if (entry.getKey().equals("product_variants")) {
                    assertThat(after).containsAll(entry.getValue()).hasSize(entry.getValue().size() + 1);
                } else {
                    assertThat(after).as("All non-FK columns of %s", entry.getKey()).isEqualTo(entry.getValue());
                }
            }
            Map<String, List<List<String>>> once = new HashMap<>();
            for (String table : before.keySet()) once.put(table, snapshot(c, table));
            migrate(c);
            runChecks(c, checks);
            for (String table : once.keySet()) assertThat(snapshot(c, table)).as(table).isEqualTo(once.get(table));
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT i.variant_id,c.variant_id,e.target_variant_id,v.id,v.stock_quantity FROM order_items i JOIN cart_items c ON c.product_id=i.product_id JOIN exchange_request_items e ON e.order_item_id=i.id JOIN product_variants v ON v.product_id=i.product_id WHERE v.combination_key='__DEFAULT__'")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getLong(1)).isEqualTo(r.getLong(4));
                assertThat(r.getLong(2)).isEqualTo(r.getLong(4));
                assertThat(r.getLong(3)).isEqualTo(r.getLong(4));
                assertThat(r.getInt(5)).isEqualTo(7);
            }
            // Historical imports with FK checks disabled must be identified by
            // exact row ID, not disappear through an INNER JOIN.
            long exchangeId;
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT id FROM exchange_request_items")) {
                assertThat(r.next()).isTrue(); exchangeId = r.getLong(1);
            }
            try (var s = c.createStatement()) {
                s.execute("SET FOREIGN_KEY_CHECKS=0");
                s.execute("UPDATE exchange_request_items SET target_variant_id=9223372036854775807 WHERE id=" + exchangeId);
                s.execute("SET FOREIGN_KEY_CHECKS=1");
            }
            boolean identified = false;
            for (String query : checks.replaceAll("(?m)^\\s*--.*$", "").split(";")) {
                if (!query.contains("ORPHAN_ORDER_PRODUCT_OR_VARIANT")) continue;
                try (var s = c.createStatement(); var r = s.executeQuery(query)) {
                    while (r.next()) {
                        if (r.getString("source").equals("EXCHANGE") && r.getLong("id") == exchangeId) identified = true;
                    }
                }
            }
            assertThat(identified).isTrue();
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(java.sql.SQLException.class).hasMessageContaining("Orphan");
        }
    }

    private void migrate(Connection c) throws Exception {
        String sql = Files.readString(Path.of("../docs/sql/product-default-variant.sql"));
        try (var s = c.createStatement()) {
            s.execute("DROP PROCEDURE IF EXISTS migrate_product_default_variant");
            s.execute(sql.substring(sql.indexOf("CREATE PROCEDURE"), sql.indexOf("END$$") + 3));
            s.execute("CALL migrate_product_default_variant()");
        }
    }

    private void runChecks(Connection c, String sql) throws Exception {
        // Execute every shipped read-only statement, including the metadata and
        // financial exports, to catch actual column names/ONLY_FULL_GROUP_BY issues.
        String withoutComments = sql.replaceAll("(?m)^\\s*--.*$", "");
        for (String query : withoutComments.split(";")) {
            if (query.isBlank()) continue;
            try (var s = c.createStatement(); var r = s.executeQuery(query)) {
                if (query.matches("(?is).*\\bAS\\s+reason\\b.*")) {
                    assertThat(r.next()).as("No anomalies: %s", query).isFalse();
                }
                while (r.next()) { /* SQL validity only; snapshots verified separately. */ }
            }
        }
    }

    private List<List<String>> snapshot(Connection c, String table) throws Exception {
        Set<String> allowed = switch (table) {
            case "order_items", "cart_items" -> Set.of("variant_id");
            case "exchange_request_items" -> Set.of("target_variant_id");
            default -> Set.of();
        };
        var result = new ArrayList<List<String>>();
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
            while (r.next()) {
                var row = new ArrayList<String>();
                for (int col = 1; col <= r.getMetaData().getColumnCount(); col++) {
                    if (!allowed.contains(r.getMetaData().getColumnName(col))) row.add(r.getString(col));
                }
                result.add(row);
            }
        }
        return result;
    }

    private void fixture() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            User user = persist(User.createOAuthUser("migration@example.test", "migration", null, AuthProvider.GOOGLE, "migration-test"));
            Seller seller = persist(Seller.create(user, "migration", "test"));
            Category category = persist(Category.create(null, "migration", 0));
            Product plain = persist(Product.createDraft(seller, category, "plain", null, null, "test", 10000L, 7, null, true, 0L, 3, 3000L, 6000L));
            Product option = persist(Product.createDraft(seller, category, "option", null, null, "test", 10000L, 11, null, true, 0L, 3, 3000L, 6000L));
            ProductOptionGroup group = persist(ProductOptionGroup.create(option, "color", 0));
            ProductOptionValue value = persist(ProductOptionValue.create(group, "red", 0));
            ProductVariant variant = persist(ProductVariant.create(option, "RED", group.getId() + ":" + value.getId(), 100L, 11));
            persist(ProductVariantOptionValue.create(variant, value));
            Order order = persist(Order.createPendingPayment("MIGRATION-ORDER", user, 40000L, 0L, "recipient", "010-1234-5678", "12345", "test address", null));
            order.markPaid(now);
            SellerOrder sellerOrder = persist(SellerOrder.createPendingPayment(order, seller));
            sellerOrder.markPaid();
            OrderItem item = persist(OrderItem.create(order, plain, null, seller, sellerOrder, null, "plain snapshot", null, "store snapshot", null, null, 10000L, 0L, 4, true, 0L, 3000L, 6000L));
            persist(CartItem.create(user, plain, null, 2));
            Payment payment = persist(Payment.createReady(order, PaymentProvider.TOSS, "migration-merchant", "migration-request", "migration-confirm", 40000L, "KRW", now, now.plusHours(1)));
            payment.complete("test-payment-key", "test-transaction", PaymentMethod.CARD, null, "DONE", now);
            OrderCancellation cancellation = persist(OrderCancellation.createProcessing(order, sellerOrder, "migration-cancel", "test", now));
            persist(OrderCancellationItem.create(cancellation, item, 1));
            PaymentCancellation refund = persist(PaymentCancellation.createPartial(payment, cancellation, "migration-refund", "migration-refund-idempotent", 10000L, "test", now));
            refund.succeed("test-refund-transaction", now);
            cancellation.complete(now);
            item.confirmCancellation(1);
            ExchangeRequest exchange = persist(ExchangeRequest.createRequested(order, sellerOrder, "migration-exchange", ExchangeReasonType.DEFECTIVE, "test", "recipient", "010-1234-5678", "12345", "test address", null, "recipient", "010-1234-5678", "12345", "test address", null, now));
            persist(ExchangeRequestItem.create(exchange, item, 1, plain, null, "target snapshot", null, 10000L));
            ReturnRequest returned = persist(ReturnRequest.createRequested(order, sellerOrder, "migration-return", ReturnReasonType.DEFECTIVE, "test", "recipient", "010-1234-5678", "12345", "test address", null, now));
            persist(ReturnRequestItem.create(returned, item, 1));
        });
    }

    private <T> T persist(T entity) { entityManager.persist(entity); return entity; }
}
