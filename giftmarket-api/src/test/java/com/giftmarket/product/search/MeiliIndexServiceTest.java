package com.giftmarket.product.search;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MeiliIndexServiceTest {
    JdbcTemplate jdbc;
    MeiliClient client;
    MeiliIndexService index;
    MeiliProperties config;
    @Test void unchangedScanKeepsSequentialAndRepeatedSearchesOnTheSameEngine() {
        jdbc.update("insert into products values (2,1,1,'Draft',null,null,null,'DRAFT',null,false)");
        index.reindex(false);
        var products = mock(com.giftmarket.product.service.ProductService.class);
        var reader = mock(SearchProductReader.class);
        var search = new ProductSearchService(products, config, client, reader, index);
        var bedding = List.of(mock(com.giftmarket.product.dto.response.ProductSummaryResponse.class),
                mock(com.giftmarket.product.dto.response.ProductSummaryResponse.class),
                mock(com.giftmarket.product.dto.response.ProductSummaryResponse.class));
        when(client.search(anyString())).thenReturn(List.of(1L));
        when(reader.read(anyList(), any(), anyBoolean())).thenAnswer(invocation -> {
            var condition = invocation.getArgument(1, com.giftmarket.product.dto.request.ProductSearchCondition.class);
            var rows = condition.normalizedKeyword().equals("침구") ? bedding : bedding.subList(0, 2);
            return new com.giftmarket.product.dto.response.ProductPageResponse(rows, 0, 20, rows.size(), 1, true, true);
        });
        // Run searches while the periodic scan is in progress, before it completes.
        doAnswer(invocation -> {
            assertThat(index.ready()).isTrue();
            for (String keyword : List.of("침구", "침구류", "침구", "침구")) {
                var condition = new com.giftmarket.product.dto.request.ProductSearchCondition(
                        List.of(), null, keyword, null, null, null, false, 0, 20);
                var result = search.search(condition);
                assertThat(result.totalElements()).isEqualTo(keyword.equals("침구") ? 3 : 2);
                assertThat(search.suggestions(keyword)).contains(result.products());
            }
            verifyNoInteractions(products);
            return null;
        }).when(client).upsert(List.of());
        assertThat(index.reindex(false).status()).isEqualTo("COMPLETE");
        verify(client, times(1)).delete(List.of(2L));
    }

    @Test void excludedProductIsDeletedOnceAndCanBecomeSearchableAgain() {
        index.reindex(false);
        jdbc.update("update products set admin_hidden=true where id=1");
        index.reindex(false);
        verify(client).delete(List.of(1L));
        clearInvocations(client);
        index.reindex(false);
        verify(client, never()).delete(argThat(ids -> !ids.isEmpty()));
        assertThat(index.ready()).isTrue();
        jdbc.update("update products set admin_hidden=false where id=1");
        index.reindex(false);
        verify(client).upsert(argThat(d -> d.size() == 1));
    }

    @Test void discoveredChangesAndFailedScansStillInvalidateTheIndex() {
        index.reindex(false);
        jdbc.update("update products set name='Changed without event' where id=1");
        doAnswer(invocation -> {
            assertThat(index.ready()).isFalse();
            throw new IllegalStateException("unavailable");
        }).when(client).upsert(argThat(d -> !d.isEmpty()));
        assertThat(index.reindex(false).status()).isEqualTo("RETRY");
        assertThat(index.ready()).isFalse();
    }

    @Test void verificationFailureDoesNotLeaveThePreviousIndexMarkedReady() {
        index.reindex(false);
        jdbc.execute("drop table products");
        assertThat(index.reindex(false).status()).isEqualTo("RETRY");
        assertThat(index.ready()).isFalse();
    }
    @Test void settingsOnlyUpdateKeepsReadyIndexAndDoesNotReuploadDocuments() {
        index.reindex(false);
        assertThat(index.ready()).isTrue();
        clearInvocations(client);
        when(client.synonyms()).thenReturn(Map.of(), config.effectiveSynonyms());
        assertThat(index.settings(true).status()).isEqualTo("APPLIED");
        assertThat(index.ready()).isTrue();
        verify(client).updateSynonyms(config.effectiveSynonyms());
        verify(client, times(2)).synonyms();
        verifyNoMoreInteractions(client);
    }
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:meili_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("create table sellers(id bigint, status varchar(20))");
        jdbc.execute("create table categories(id bigint, name varchar(100))");
        jdbc.execute("create table products(id bigint, seller_id bigint, category_id bigint, name varchar(200), brand_name varchar(100), summary varchar(500), description clob, status varchar(20), deleted_at timestamp, admin_hidden boolean)");
        jdbc.execute("create table gift_occasions(id bigint,name varchar(100),active boolean)");
        jdbc.execute("create table product_gift_occasions(product_id bigint,gift_occasion_id bigint)");
        jdbc.update("insert into sellers values (1,'ACTIVE')");
        jdbc.update("insert into categories values (1,'Bedding')");
        jdbc.update("insert into products values (1,1,1,'Fabric Set',null,'Sleep','<p>Actual description</p>','ON_SALE',null,false)");
        client = mock(MeiliClient.class);
        config = new MeiliProperties(); config.setEngine("meilisearch");
        index = new MeiliIndexService(jdbc, client, config);
    }
    @Test void mysqlDisablesIndexingByDefault() {
        config.setEngine("mysql");
        assertThat(index.reindex(true)).isEqualTo(new MeiliIndexService.Result(0, "DISABLED", 0));
        index.synchronize();
        assertThat(index.ready()).isFalse();
        verifyNoInteractions(client);
    }
    @Test void mysqlCanIndexAndSynchronizeWhenExplicitlyEnabled() {
        config.setEngine("mysql");
        config.setIndexingEnabled(true);
        assertThat(config.enabled()).isFalse();
        assertThat(index.reindex(true).status()).isEqualTo("COMPLETE");
        verify(client).configure();
        clearInvocations(client);
        index.changed();
        index.synchronize();
        assertThat(index.ready()).isTrue();
        verify(client).upsert(anyList());
    }
    @Test void indexesChangesDeletesAndSkipsUnchangedIdempotently() {
        assertThat(index.reindex(false).status()).isEqualTo("COMPLETE");
        assertThat(index.ready()).isTrue();
        verify(client).upsert(argThat(d -> d.size()==1 && d.getFirst().get("description").equals("Actual description")));
        clearInvocations(client);
        index.reindex(false);
        verify(client).upsert(List.of());
        jdbc.update("update products set name='Updated' where id=1"); index.changed();
        assertThat(index.ready()).isFalse();
        index.reindex(false);
        verify(client).upsert(argThat(d -> d.size()==1 && d.getFirst().get("name").equals("Updated")));
        jdbc.update("update products set deleted_at=current_timestamp where id=1"); index.changed();
        index.reindex(false);
        verify(client).delete(List.of(1L));
    }
    @Test void failureRetainsCursorAndRecoveryRechecksDatabase() {
        doThrow(new IllegalStateException()).when(client).upsert(anyList());
        assertThat(index.reindex(false).status()).isEqualTo("RETRY");
        assertThat(index.ready()).isFalse();
        doNothing().when(client).upsert(anyList());
        assertThat(index.reindex(false).status()).isEqualTo("COMPLETE");
        assertThat(index.ready()).isTrue();
    }
    @Test void concurrentReindexReturnsBusyWithoutDuplicateWriters() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); release.await(3, java.util.concurrent.TimeUnit.SECONDS); return null; }).when(client).configure();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> index.reindex(false));
            assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try { assertThat(index.reindex(false).status()).isEqualTo("BUSY"); }
            finally { release.countDown(); }
            assertThat(first.get().status()).isEqualTo("COMPLETE");
        }
    }
}
