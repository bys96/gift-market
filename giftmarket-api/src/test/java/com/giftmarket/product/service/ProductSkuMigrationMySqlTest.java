package com.giftmarket.product.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@Tag("mysql-container")
@Testcontainers
class ProductSkuMigrationMySqlTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    @org.junit.jupiter.api.BeforeEach
    void resetDisposableSchema() throws Exception {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS product_option_values");
            statement.execute("DROP TABLE IF EXISTS product_option_groups");
            statement.execute("DROP TABLE IF EXISTS history");
            statement.execute("DROP TABLE IF EXISTS product_variants");
        }
    }

    @Test
    void optionArchiveDdlCanResumeAfterPartialFailureAndPreservesExistingFlags() throws Exception {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE product_option_groups (id BIGINT PRIMARY KEY, name VARCHAR(50))");
            statement.execute("INSERT INTO product_option_groups VALUES (4,'color')");
            String sql = Files.readString(Path.of("../docs/sql/product-option-archive.sql"));
            statement.execute("DROP PROCEDURE IF EXISTS migrate_product_option_archive");
            statement.execute(sql.substring(sql.indexOf("CREATE PROCEDURE"), sql.indexOf("END$$") + 3));
            // First ALTER commits, second fails because the values table is absent.
            assertThatThrownBy(() -> statement.execute("CALL migrate_product_option_archive()"))
                    .isInstanceOf(SQLException.class);
            statement.execute("UPDATE product_option_groups SET archived=true WHERE id=4");
            statement.execute("CREATE TABLE product_option_values (id BIGINT PRIMARY KEY, value VARCHAR(100))");
            statement.execute("INSERT INTO product_option_values VALUES (6,'red')");
            statement.execute("CALL migrate_product_option_archive()");
            statement.execute("CALL migrate_product_option_archive()");
            try (var rows = statement.executeQuery("SELECT g.id,g.name,g.archived,v.value,v.archived FROM product_option_groups g CROSS JOIN product_option_values v")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(4);
                assertThat(rows.getString(2)).isEqualTo("color");
                assertThat(rows.getBoolean(3)).isTrue();
                assertThat(rows.getString(4)).isEqualTo("red");
                assertThat(rows.getBoolean(5)).isFalse();
            }
            statement.execute("DROP PROCEDURE migrate_product_option_archive");
        }
    }

    @Test
    void refusesReservedLegacyNamespaceAndNoncanonicalImportsWithoutChangingRows() throws Exception {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE product_variants (id BIGINT PRIMARY KEY, product_id BIGINT, "
                    + "sku_code VARCHAR(100) NOT NULL UNIQUE)");
            statement.execute("INSERT INTO product_variants VALUES (123,48,'GM-LEGACY')");
            assertThatThrownBy(() -> migrate(connection)).isInstanceOf(SQLException.class)
                    .hasMessageContaining("namespace");
            statement.execute("UPDATE product_variants SET sku_code=' f1 ' WHERE id=123");
            assertThatThrownBy(() -> migrate(connection)).isInstanceOf(SQLException.class)
                    .hasMessageContaining("Noncanonical");
            try (var rows = statement.executeQuery("SELECT id,sku_code FROM product_variants")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(123);
                assertThat(rows.getString(2)).isEqualTo(" f1 ");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private void migrate(Connection connection) throws Exception {
        String sql = Files.readString(Path.of("../docs/sql/product-variant-seller-sku.sql"));
        try (var statement = connection.createStatement()) {
            statement.execute("DROP PROCEDURE IF EXISTS migrate_product_variant_seller_sku");
            statement.execute(sql.substring(sql.indexOf("CREATE PROCEDURE"), sql.indexOf("END$$") + 3));
            statement.execute("CALL migrate_product_variant_seller_sku()");
            statement.execute("DROP PROCEDURE migrate_product_variant_seller_sku");
        }
    }

    @Test
    void actualMigrationPreservesReferencesAndEnforcesUniqueDuringConcurrentWrites() throws Exception {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE product_variants (id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                    + "product_id BIGINT NOT NULL, sku_code VARCHAR(100) NOT NULL UNIQUE, "
                    + "stock_quantity INT NOT NULL, active BOOLEAN NOT NULL)");
            statement.execute("INSERT INTO product_variants VALUES (123,48,'F1',17,false)");
            statement.execute("CREATE TABLE history (id BIGINT PRIMARY KEY, variant_id BIGINT, "
                    + "option_snapshot VARCHAR(100), settlement_amount BIGINT, "
                    + "FOREIGN KEY (variant_id) REFERENCES product_variants(id))");
            statement.execute("INSERT INTO history VALUES (1,123,'original option',12500)");
            migrate(connection);
            migrate(connection);
            try (var columns = statement.executeQuery("SELECT collation_name FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='product_variants' AND column_name='seller_sku_code'")) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getString(1)).isEqualTo("utf8mb4_0900_as_ci");
            }
            insert(connection, 52, "CAFE");
            insert(connection, 52, "CAFÉ"); // accents remain distinct
            assertThatThrownBy(() -> insert(connection, 52, "café"))
                    .isInstanceOf(SQLException.class); // case is insensitive
            try (var rows = statement.executeQuery("SELECT v.*, h.option_snapshot, h.settlement_amount "
                    + "FROM product_variants v JOIN history h ON h.variant_id=v.id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("id")).isEqualTo(123);
                assertThat(rows.getLong("product_id")).isEqualTo(48);
                assertThat(rows.getString("sku_code")).isEqualTo("F1");
                assertThat(rows.getString("seller_sku_code")).isEqualTo("F1");
                assertThat(rows.getInt("stock_quantity")).isEqualTo(17);
                assertThat(rows.getBoolean("active")).isFalse();
                assertThat(rows.getString("option_snapshot")).isEqualTo("original option");
                assertThat(rows.getLong("settlement_amount")).isEqualTo(12500);
                assertThat(rows.next()).isFalse();
            }
            assertThatThrownBy(() -> insert(connection, 48, "F1"))
                    .isInstanceOf(SQLException.class); // inactive row still owns F1
            assertThatThrownBy(() -> insert(connection, 48, "f1"))
                    .isInstanceOf(SQLException.class); // DB also rejects case variants
            insert(connection, 49, "F1");
            try (var pool = Executors.newFixedThreadPool(2)) {
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                java.util.concurrent.Callable<Boolean> attempt = () -> {
                    try (Connection concurrent = connect()) {
                        ready.countDown();
                        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                        try { insert(concurrent, 50, "RED"); return true; }
                        catch (SQLException duplicate) {
                            assertThat(duplicate.getSQLState()).isEqualTo("23000");
                            return false;
                        }
                    }
                };
                var first = pool.submit(attempt);
                var second = pool.submit(attempt);
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(true, false);
            }
            statement.execute("UPDATE product_variants SET seller_sku_code='BLUE' WHERE id=123");
            migrate(connection);
            try (var rows = statement.executeQuery("SELECT sku_code,seller_sku_code FROM product_variants WHERE id=123")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("F1");
                assertThat(rows.getString(2)).isEqualTo("BLUE");
            }
            assertThatThrownBy(() -> statement.execute("INSERT INTO product_variants "
                    + "(product_id,sku_code,seller_sku_code,stock_quantity,active) VALUES (51,'F1','OTHER',1,true)"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.execute("INSERT INTO product_variants "
                    + "(product_id,sku_code,seller_sku_code,stock_quantity,active) VALUES (51,NULL,'OTHER',1,true)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void insert(Connection connection, long productId, String sellerCode) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO product_variants "
                + "(product_id,sku_code,seller_sku_code,stock_quantity,active) VALUES (?,?,?,3,true)")) {
            statement.setLong(1, productId);
            statement.setString(2, "GM-" + UUID.randomUUID());
            statement.setString(3, sellerCode);
            statement.executeUpdate();
        }
    }
}
