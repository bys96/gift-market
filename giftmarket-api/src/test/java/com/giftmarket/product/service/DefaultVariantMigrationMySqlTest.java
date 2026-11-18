package com.giftmarket.product.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

@Tag("mysql-container")
@Testcontainers
class DefaultVariantMigrationMySqlTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--log-bin-trust-function-creators=1");

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    @BeforeEach
    void disposableSchema() throws Exception {
        try (var c = connect(); var s = c.createStatement()) {
            for (String table : new String[]{"exchange_request_items", "cart_items", "order_items",
                    "product_variant_option_values", "product_option_values", "product_option_groups", "product_variants", "products"}) {
                s.execute("DROP TABLE IF EXISTS " + table);
            }
            s.execute("CREATE TABLE products(id BIGINT PRIMARY KEY,stock_quantity INT NOT NULL)");
            s.execute("CREATE TABLE product_variants(id BIGINT PRIMARY KEY AUTO_INCREMENT,product_id BIGINT NOT NULL,"
                    + "sku_code VARCHAR(100) NOT NULL UNIQUE,seller_sku_code VARCHAR(100) NOT NULL,combination_key VARCHAR(500) NOT NULL,"
                    + "additional_price BIGINT NOT NULL,stock_quantity INT NOT NULL,active BOOLEAN NOT NULL,version BIGINT,created_at DATETIME(6),updated_at DATETIME(6),"
                    + "UNIQUE(product_id,combination_key),UNIQUE(product_id,seller_sku_code),FOREIGN KEY(product_id) REFERENCES products(id))");
            s.execute("CREATE TABLE product_option_groups(id BIGINT PRIMARY KEY,product_id BIGINT)");
            s.execute("CREATE TABLE product_option_values(id BIGINT PRIMARY KEY,option_group_id BIGINT)");
            s.execute("CREATE TABLE product_variant_option_values(variant_id BIGINT,option_value_id BIGINT)");
            s.execute("CREATE TABLE order_items(id BIGINT PRIMARY KEY,product_id BIGINT,variant_id BIGINT,option_snapshot VARCHAR(100),"
                    + "additional_price BIGINT,unit_price BIGINT,canceled_quantity INT,FOREIGN KEY(variant_id) REFERENCES product_variants(id))");
            s.execute("CREATE TABLE cart_items(id BIGINT PRIMARY KEY,user_id BIGINT,product_id BIGINT,variant_id BIGINT,FOREIGN KEY(variant_id) REFERENCES product_variants(id))");
            s.execute("CREATE TABLE exchange_request_items(id BIGINT PRIMARY KEY,order_item_id BIGINT,target_product_id BIGINT,target_variant_id BIGINT,"
                    + "target_option_snapshot VARCHAR(100),reserved_quantity INT,released_quantity INT,restocked_quantity INT,FOREIGN KEY(target_variant_id) REFERENCES product_variants(id))");
            s.execute("INSERT INTO products VALUES(1,7),(2,11)");
            s.execute("INSERT INTO product_variants VALUES(40,2,'EXISTING-SKU','EXISTING-SELLER','20:21',100,11,true,9,NOW(6),NOW(6))");
            s.execute("INSERT INTO product_option_groups VALUES(20,2)");
            s.execute("INSERT INTO product_option_values VALUES(21,20)");
            s.execute("INSERT INTO product_variant_option_values VALUES(40,21)");
            // Include an already cancelled historical item: migration must not restock it.
            s.execute("INSERT INTO order_items VALUES(1,1,NULL,NULL,0,10000,2),(2,2,40,'original',100,10100,0)");
            s.execute("INSERT INTO cart_items VALUES(1,5,1,NULL)");
            s.execute("INSERT INTO exchange_request_items VALUES(1,1,1,NULL,NULL,2,2,2)");
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

    @Test
    void copiesAvailableStockOnceAndOnlyBackfillsForeignKeys() throws Exception {
        try (var c = connect(); var s = c.createStatement()) {
            s.execute("INSERT INTO product_option_values VALUES(22,20)");
            s.execute("INSERT INTO product_variants VALUES(50,2,'INACTIVE-SKU','INACTIVE-SELLER','20:22',200,5,false,8,NOW(6),NOW(6))");
            s.execute("INSERT INTO product_variant_option_values VALUES(50,22)");
            var originalVariant = snapshot(c, "product_variants", Set.of());
            var originalProducts = snapshot(c, "products", Set.of());
            var originalOrders = snapshot(c, "order_items", Set.of("variant_id"));
            var originalCarts = snapshot(c, "cart_items", Set.of("variant_id"));
            var originalExchanges = snapshot(c, "exchange_request_items", Set.of("target_variant_id"));
            migrate(c);
            String before;
            try (var r = s.executeQuery("SELECT CONCAT_WS('|',id,sku_code,stock_quantity,version) FROM product_variants WHERE product_id=1")) {
                assertThat(r.next()).isTrue(); before = r.getString(1);
                assertThat(before).contains("GM-").endsWith("|7|0");
            }
            migrate(c);
            assertThat(snapshot(c, "products", Set.of())).isEqualTo(originalProducts);
            assertThat(snapshot(c, "order_items", Set.of("variant_id"))).isEqualTo(originalOrders);
            assertThat(snapshot(c, "cart_items", Set.of("variant_id"))).isEqualTo(originalCarts);
            assertThat(snapshot(c, "exchange_request_items", Set.of("target_variant_id"))).isEqualTo(originalExchanges);
            assertThat(snapshot(c, "product_variants", Set.of())).containsAll(originalVariant);
            try (var r = s.executeQuery("SELECT CONCAT_WS('|',id,sku_code,stock_quantity,version) FROM product_variants WHERE product_id=1")) {
                assertThat(r.next()).isTrue(); assertThat(r.getString(1)).isEqualTo(before); assertThat(r.next()).isFalse();
            }
            try (var r = s.executeQuery("SELECT i.variant_id,v.id,i.unit_price,i.option_snapshot,i.canceled_quantity,p.stock_quantity,e.target_variant_id,e.reserved_quantity,e.released_quantity,e.restocked_quantity,c.variant_id FROM order_items i JOIN products p ON p.id=i.product_id JOIN product_variants v ON v.product_id=p.id JOIN exchange_request_items e ON e.order_item_id=i.id JOIN cart_items c ON c.product_id=p.id WHERE i.id=1")) {
                assertThat(r.next()).isTrue(); assertThat(r.getLong(1)).isEqualTo(r.getLong(2));
                assertThat(r.getLong(3)).isEqualTo(10000); assertThat(r.getString(4)).isNull();
                assertThat(r.getInt(5)).isEqualTo(2); assertThat(r.getInt(6)).isEqualTo(7);
                assertThat(r.getLong(7)).isEqualTo(r.getLong(2));
                assertThat(r.getInt(8)).isEqualTo(2); assertThat(r.getInt(9)).isEqualTo(2); assertThat(r.getInt(10)).isEqualTo(2);
                assertThat(r.getLong(11)).isEqualTo(r.getLong(2));
            }
            try (var r = s.executeQuery("SELECT v.*,i.option_snapshot,i.unit_price FROM product_variants v JOIN order_items i ON i.variant_id=v.id WHERE v.id=40")) {
                assertThat(r.next()).isTrue(); assertThat(r.getString("sku_code")).isEqualTo("EXISTING-SKU");
                assertThat(r.getString("seller_sku_code")).isEqualTo("EXISTING-SELLER");
                assertThat(r.getInt("stock_quantity")).isEqualTo(11); assertThat(r.getLong("version")).isEqualTo(9);
                assertThat(r.getString("option_snapshot")).isEqualTo("original"); assertThat(r.getLong("unit_price")).isEqualTo(10100);
            }
        }
    }

    // Data lives only in this test's disposable container. Compare every column,
    // including timestamps/version/active state, except the explicitly changed FK.
    private List<List<String>> snapshot(Connection c, String table, Set<String> excluded) throws Exception {
        var result = new ArrayList<List<String>>();
        try (var s = c.createStatement(); var rows = s.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
            while (rows.next()) {
                var row = new ArrayList<String>();
                for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                    if (!excluded.contains(rows.getMetaData().getColumnName(column))) row.add(rows.getString(column));
                }
                result.add(row);
            }
        }
        return result;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"combination", "seller", "system", "prefix", "extra-column"})
    void refusesMissingOrWeakenedUniqueIndexesBeforeWriting(String kind) throws Exception {
        try (var c = connect(); var s = c.createStatement()) {
            String column = switch (kind) {
                case "seller" -> "seller_sku_code";
                case "system" -> "sku_code";
                default -> "combination_key";
            };
            String index = indexFor(c, column);
            s.execute("ALTER TABLE product_variants DROP INDEX `" + index + "`");
            if (kind.equals("prefix")) s.execute("CREATE UNIQUE INDEX wrong_prefix ON product_variants(product_id,combination_key(10))");
            if (kind.equals("extra-column")) s.execute("CREATE UNIQUE INDEX wrong_extra ON product_variants(product_id,combination_key,active)");
            var before = snapshot(c, "product_variants", Set.of());
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(SQLException.class).hasMessageContaining("Missing full UNIQUE");
            assertThat(snapshot(c, "product_variants", Set.of())).isEqualTo(before);
        }
    }

    private String indexFor(Connection c, String column) throws SQLException {
        try (var s = c.prepareStatement("SELECT index_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='product_variants' AND column_name=? AND non_unique=0")) {
            s.setString(1, column);
            try (var r = s.executeQuery()) { assertThat(r.next()).isTrue(); return r.getString(1); }
        }
    }

    @Test
    void reportsDuplicateDefaultsIncludingInactiveRowsRatherThanChoosingOne() throws Exception {
        try (var c = connect(); var s = c.createStatement()) {
            s.execute("ALTER TABLE product_variants DROP INDEX `" + indexFor(c, "combination_key") + "`");
            s.execute("INSERT INTO product_variants VALUES(41,1,CONCAT('GM-',UUID()),'DEFAULT-1','__DEFAULT__',0,7,true,0,NOW(6),NOW(6)),(42,1,CONCAT('GM-',UUID()),'OTHER','__DEFAULT__',0,5,false,0,NOW(6),NOW(6))");
            var before = snapshot(c, "product_variants", Set.of());
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(SQLException.class).hasMessageContaining("Duplicate default");
            assertThat(snapshot(c, "product_variants", Set.of())).isEqualTo(before);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"order-variant", "cart-variant", "exchange-variant", "exchange-order", "product"})
    void refusesOrphanReferencesEvenIfForeignKeysHadPreviouslyBeenDisabled(String kind) throws Exception {
        try (var c = connect(); var s = c.createStatement()) {
            s.execute("SET FOREIGN_KEY_CHECKS=0");
            s.execute(switch (kind) {
                case "order-variant" -> "UPDATE order_items SET variant_id=999 WHERE id=1";
                case "cart-variant" -> "UPDATE cart_items SET variant_id=999 WHERE id=1";
                case "exchange-variant" -> "UPDATE exchange_request_items SET target_variant_id=999 WHERE id=1";
                case "exchange-order" -> "UPDATE exchange_request_items SET order_item_id=999 WHERE id=1";
                default -> "UPDATE order_items SET product_id=999 WHERE id=1";
            });
            s.execute("SET FOREIGN_KEY_CHECKS=1");
            var before = snapshot(c, "order_items", Set.of());
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(SQLException.class).hasMessageContaining("Orphan");
            assertThat(snapshot(c, "order_items", Set.of())).isEqualTo(before);
            assertThat(snapshot(c, "product_variants", Set.of())).hasSize(1);
        }
    }

    @Test
    void refusesMissingForeignKeyAndTriggerSideEffects() throws Exception {
        try (var c = connect(); var s = c.createStatement()) {
            String fk;
            try (var r = s.executeQuery("SELECT constraint_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='order_items' AND column_name='variant_id' AND referenced_table_name='product_variants'")) {
                assertThat(r.next()).isTrue(); fk = r.getString(1);
            }
            s.execute("ALTER TABLE order_items DROP FOREIGN KEY `" + fk + "`");
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(SQLException.class).hasMessageContaining("Missing expected");
            s.execute("ALTER TABLE order_items ADD FOREIGN KEY(variant_id) REFERENCES product_variants(id)");
            s.execute("CREATE TRIGGER unsafe_migration_trigger BEFORE UPDATE ON order_items FOR EACH ROW SET NEW.unit_price=1");
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(SQLException.class).hasMessageContaining("Schema side effects");
            assertThat(snapshot(c, "product_variants", Set.of())).hasSize(1);
        }
    }

    @Test
    void rollsBackDefaultAndEarlierForeignKeyUpdatesWhenLastUpdateFails() throws Exception {
        try (var c = connect(); var s = c.createStatement()) {
            s.execute("ALTER TABLE cart_items ADD CONSTRAINT test_block_write CHECK (variant_id IS NULL)");
            var before = snapshot(c, "order_items", Set.of());
            var exchanges = snapshot(c, "exchange_request_items", Set.of());
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(SQLException.class);
            assertThat(snapshot(c, "order_items", Set.of())).isEqualTo(before);
            assertThat(snapshot(c, "exchange_request_items", Set.of())).isEqualTo(exchanges);
            assertThat(snapshot(c, "product_variants", Set.of())).hasSize(1);
            s.execute("ALTER TABLE cart_items DROP CHECK test_block_write");
            migrate(c);
            migrate(c);
            assertThat(snapshot(c, "product_variants", Set.of())).hasSize(2);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"uuid", "seller-case", "key-case", "active-with-options", "order-snapshot"})
    void refusesMalformedOrContradictoryDefaultState(String kind) throws Exception {
        try (var c = connect(); var s = c.createStatement()) {
            migrate(c);
            s.execute(switch (kind) {
                case "uuid" -> "UPDATE product_variants SET sku_code=CONCAT('GM-',REPEAT('-',36)) WHERE product_id=1";
                case "seller-case" -> "UPDATE product_variants SET seller_sku_code='default-1' WHERE product_id=1";
                case "key-case" -> "UPDATE product_variants SET combination_key='__default__' WHERE product_id=1";
                case "active-with-options" -> "INSERT INTO product_option_groups VALUES(30,1)";
                default -> "UPDATE order_items SET option_snapshot='unexpected option' WHERE id=1";
            });
            var before = snapshot(c, "product_variants", Set.of());
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(SQLException.class)
                    .hasMessageContaining(kind.equals("order-snapshot") ? "Ambiguous order" : "Reserved default");
            assertThat(snapshot(c, "product_variants", Set.of())).isEqualTo(before);
        }
    }

    @Test
    void rerunsAfterOptionConversionPreserveRetainedStockButRejectMixedExchangeTypes() throws Exception {
        try (var c = connect(); var s = c.createStatement()) {
            migrate(c);
            s.execute("UPDATE product_variants SET active=false WHERE product_id=1");
            s.execute("INSERT INTO product_option_groups VALUES(30,1)");
            s.execute("INSERT INTO product_option_values VALUES(31,30)");
            s.execute("INSERT INTO product_variants VALUES(100,1,'NEW-OPTION-SKU','NEW-OPTION','30:31',0,3,true,0,NOW(6),NOW(6))");
            s.execute("INSERT INTO product_variant_option_values VALUES(100,31)");
            s.execute("UPDATE products SET stock_quantity=3 WHERE id=1");
            var before = snapshot(c, "product_variants", Set.of());
            migrate(c);
            migrate(c);
            assertThat(snapshot(c, "product_variants", Set.of())).isEqualTo(before);
            s.execute("UPDATE exchange_request_items SET target_variant_id=100 WHERE id=1");
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(SQLException.class).hasMessageContaining("Ambiguous exchange");
            assertThat(snapshot(c, "product_variants", Set.of())).isEqualTo(before);
        }
    }

    @Test
    void refusesDisabledFkChecksAndAutomaticTimestampUpdates() throws Exception {
        try (var c = connect(); var s = c.createStatement()) {
            s.execute("SET FOREIGN_KEY_CHECKS=0");
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(SQLException.class).hasMessageContaining("Schema safety");
            s.execute("SET FOREIGN_KEY_CHECKS=1");
            s.execute("ALTER TABLE product_variants MODIFY updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)");
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(SQLException.class).hasMessageContaining("Schema side effects");
            assertThat(snapshot(c, "product_variants", Set.of())).hasSize(1);
        }
    }

    @Test
    void rejectsMixedHistoryAndMappingDamageWithoutPartialWrites() throws Exception {
        try (var c = connect(); var s = c.createStatement()) {
            s.execute("INSERT INTO order_items VALUES(3,2,NULL,NULL,0,10000,0)");
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(SQLException.class).hasMessageContaining("Ambiguous order");
            s.execute("DELETE FROM order_items WHERE id=3");
            s.execute("DELETE FROM product_variant_option_values");
            assertThatThrownBy(() -> migrate(c)).isInstanceOf(SQLException.class).hasMessageContaining("mapping mismatch");
            try (var r = s.executeQuery("SELECT COUNT(*) FROM product_variants WHERE product_id=1")) {
                assertThat(r.next()).isTrue(); assertThat(r.getInt(1)).isZero();
            }
        }
    }
}
