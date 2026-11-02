package com.giftmarket.product.search;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductEmbeddingIndexerTest {
    private JdbcTemplate jdbc;
    private EmbeddingService embeddings;
    private ProductEmbeddingIndexer indexer;

    @BeforeEach
    void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:index_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table sellers(id bigint primary key, status varchar(20))");
        jdbc.execute("create table categories(id bigint primary key, name varchar(100))");
        jdbc.execute("create table products(id bigint primary key, seller_id bigint, category_id bigint, name varchar(200), summary varchar(500), description clob, status varchar(20), deleted_at timestamp, admin_hidden boolean, version bigint)");
        jdbc.execute("create table gift_occasions(id bigint primary key, name varchar(100), active boolean)");
        jdbc.execute("create table product_gift_occasions(product_id bigint, gift_occasion_id bigint)");
        jdbc.execute("create table product_embeddings(product_id bigint, model varchar(100), dimensions int, source_hash char(64), product_version bigint, embedding blob, updated_at timestamp, primary key(product_id, model, dimensions))");
        jdbc.update("insert into sellers values (1, 'ACTIVE')");
        jdbc.update("insert into categories values (1, '침구')");
        jdbc.update("insert into products values (1, 1, 1, '이불', '포근한', '<p>침구</p>', 'ON_SALE', null, false, 0)");
        embeddings = mock(EmbeddingService.class);
        indexer = new ProductEmbeddingIndexer(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)), embeddings, true, 128);
    }

    @Test
    void indexesAndSkipsUnchangedThenReindexesChangedContent() {
        when(embeddings.embed(anyString(), eq(EmbeddingService.Task.RETRIEVAL_DOCUMENT)))
                .thenReturn(Optional.of(new float[128]));
        assertThat(indexer.reindex(0, 1).indexed()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select length(embedding) from product_embeddings", Integer.class)).isEqualTo(512);
        jdbc.update("update products set version = 1 where id = 1");
        assertThat(indexer.reindex(0, 1).unchanged()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select product_version from product_embeddings", Long.class)).isEqualTo(1L);
        jdbc.update("update products set name = '새 이불' where id = 1");
        assertThat(indexer.reindex(0, 1).indexed()).isEqualTo(1);
        verify(embeddings, times(2)).embed(anyString(), eq(EmbeddingService.Task.RETRIEVAL_DOCUMENT));
    }

    @Test
    void providerFailureKeepsCursorForRetry() {
        when(embeddings.embed(anyString(), any())).thenReturn(Optional.empty(), Optional.of(new float[128]));
        var failed = indexer.reindex(0, 1);
        assertThat(failed.status()).isEqualTo("RETRY");
        assertThat(failed.nextCursor()).isZero();
        assertThat(failed.retryProductId()).isEqualTo(1);
        assertThat(indexer.reindex(failed.nextCursor(), 1).indexed()).isEqualTo(1);
    }

    @Test
    void newModelOrDimensionsCreatesSeparateIndex() {
        when(embeddings.embed(anyString(), any())).thenReturn(Optional.of(new float[128]),
                Optional.of(new float[128]), Optional.of(new float[256]));
        assertThat(indexer.reindex(0, 1).indexed()).isEqualTo(1);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        var newModel = new ProductEmbeddingIndexer(jdbc, tx, embeddings, true, 128, "other-model");
        assertThat(newModel.reindex(0, 1).indexed()).isEqualTo(1);
        var newDimensions = new ProductEmbeddingIndexer(jdbc, tx, embeddings, true, 256, "other-model");
        assertThat(newDimensions.reindex(0, 1).indexed()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from product_embeddings", Integer.class)).isEqualTo(3);
    }
}
