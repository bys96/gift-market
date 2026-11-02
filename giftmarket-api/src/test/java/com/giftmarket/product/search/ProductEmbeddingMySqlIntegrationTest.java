package com.giftmarket.product.search;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.giftmarket.product.dto.request.ProductSearchCondition;
import com.giftmarket.product.dto.response.ProductPageResponse;
import com.giftmarket.product.dto.response.ProductSummaryResponse;
import com.giftmarket.product.service.ProductService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("mysql-container")
@Testcontainers
class ProductEmbeddingMySqlIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void appliesManualDdlAndStoresAndRefreshesEmbeddings() throws Exception {
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table sellers(id bigint primary key, status varchar(20))");
        jdbc.execute("create table categories(id bigint primary key, name varchar(100))");
        jdbc.execute("create table products(id bigint primary key, seller_id bigint, category_id bigint, name varchar(200), summary varchar(500), description longtext, status varchar(20), deleted_at datetime(6), admin_hidden boolean, version bigint)");
        jdbc.execute("create table gift_occasions(id bigint primary key, name varchar(100), active boolean)");
        jdbc.execute("create table product_gift_occasions(product_id bigint, gift_occasion_id bigint)");
        jdbc.execute(Files.readString(Path.of("../docs/sql/product-embeddings.sql")));
        jdbc.update("insert into sellers values (1, 'ACTIVE')");
        jdbc.update("insert into categories values (1, '침구')");
        jdbc.update("insert into products values (1, 1, 1, '이불', '', '', 'ON_SALE', null, false, 0)");
        float[] vector = new float[128]; vector[0] = 1;
        EmbeddingService provider = (text, task) -> Optional.of(vector);
        var indexer = new ProductEmbeddingIndexer(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(ds)), provider, true, 128);
        assertThat(indexer.reindex(0, 1).indexed()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select octet_length(embedding) from product_embeddings", Integer.class)).isEqualTo(512);
        jdbc.update("update products set name = '새 이불', version = 1 where id = 1");
        assertThat(indexer.reindex(0, 1).indexed()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select product_version from product_embeddings", Long.class)).isEqualTo(1L);
        var products = mock(ProductService.class);
        var item = ProductSummaryResponse.builder().id(1L).name("새 이불").build();
        var candidate = new ProductSearchCondition(List.of(), null, null, null, null, null, false, 0, 100);
        var legacy = new ProductSearchCondition(List.of(), null, "침구", null, null, null, false, 0, 100);
        when(products.getProducts(candidate)).thenReturn(new ProductPageResponse(List.of(item), 0, 100, 1, 1, true, true));
        when(products.getProducts(legacy)).thenReturn(new ProductPageResponse(List.of(), 0, 100, 0, 0, true, true));
        var search = new ProductSearchService(products, provider, new NamedParameterJdbcTemplate(ds), true, 128, 100);
        var condition = new ProductSearchCondition(List.of(), null, "침구", null, null, null, false, 0, 20);
        assertThat(search.search(condition).products()).containsExactly(item);
        jdbc.update("update products set name = '동시 색인', version = 2 where id = 1");
        var bothStarted = new CountDownLatch(2);
        EmbeddingService simultaneous = (text, task) -> {
            bothStarted.countDown();
            try {
                if (!bothStarted.await(10, TimeUnit.SECONDS)) return Optional.empty();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            return Optional.of(vector);
        };
        var secondIndexer = new ProductEmbeddingIndexer(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(ds)), simultaneous, true, 128);
        var thirdIndexer = new ProductEmbeddingIndexer(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(ds)), simultaneous, true, 128);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> secondIndexer.reindex(0, 1));
            var b = executor.submit(() -> thirdIndexer.reindex(0, 1));
            assertThat(a.get(20, TimeUnit.SECONDS).indexed()).isEqualTo(1);
            assertThat(b.get(20, TimeUnit.SECONDS).indexed()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("select count(*) from product_embeddings", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select product_version from product_embeddings", Long.class)).isEqualTo(2L);
    }
}
