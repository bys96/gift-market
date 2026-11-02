package com.giftmarket.product.search;

import com.giftmarket.product.dto.request.ProductSearchCondition;
import com.giftmarket.product.dto.response.ProductPageResponse;
import com.giftmarket.product.dto.response.ProductSummaryResponse;
import com.giftmarket.product.entity.ProductSort;
import com.giftmarket.product.service.ProductService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductSearchServiceTest {
    @Test
    void omittedSortRanksBeforePaginationButExplicitSortKeepsSqlOrder() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:rank_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table categories(id bigint primary key, name varchar(100))");
        jdbc.execute("create table products(id bigint primary key, version bigint, category_id bigint, name varchar(200), summary varchar(500), description clob)");
        jdbc.execute("create table product_embeddings(product_id bigint, model varchar(100), dimensions int, product_version bigint, embedding blob, updated_at timestamp, source_hash varchar(64))");
        jdbc.execute("create table gift_occasions(id bigint primary key, name varchar(100), active boolean)");
        jdbc.execute("create table product_gift_occasions(product_id bigint, gift_occasion_id bigint)");
        jdbc.update("insert into categories values (1, '과자')");
        var names = List.of("디저트 세트", "초콜렛", "초콜릿 선물", "초콜릿");
        float[] vector = new float[128]; vector[0] = 1;
        for (int i = 0; i < names.size(); i++) {
            long id = i + 1; String name = names.get(i);
            jdbc.update("insert into products values (?, 0, 1, ?, null, null)", id, name);
            String hash = ProductEmbeddingIndexer.hash(ProductEmbeddingIndexer.completeText(ProductEmbeddingIndexer.baseText(name, "과자", null, null), List.of()));
            jdbc.update("insert into product_embeddings values (?, ?, 128, 0, ?, current_timestamp, ?)", id, ProductEmbeddingIndexer.MODEL, ProductEmbeddingIndexer.encode(vector), hash);
        }
        var products = mock(ProductService.class);
        var embedding = mock(EmbeddingService.class);
        var candidate = names.stream().map(name -> ProductSummaryResponse.builder().id((long) names.indexOf(name) + 1).name(name).build()).toList();
        var defaultCandidates = new ProductSearchCondition(List.of(), null, null, null, null, null, false, 0, 100);
        var defaultLegacy = new ProductSearchCondition(List.of(), null, "초콜릿", null, null, null, false, 0, 100);
        var empty = new ProductPageResponse(List.of(), 0, 100, 0, 0, true, true);
        when(products.getProducts(defaultCandidates)).thenReturn(new ProductPageResponse(candidate, 0, 100, 4, 1, true, true));
        when(products.getProducts(defaultLegacy)).thenReturn(empty);
        when(embedding.embed("초콜릿", EmbeddingService.Task.RETRIEVAL_QUERY)).thenReturn(Optional.of(vector));
        var search = new ProductSearchService(products, embedding, new NamedParameterJdbcTemplate(ds), true, 128, 100);
        var condition = new ProductSearchCondition(List.of(), null, "초콜릿", null, null, null, false, 0, 2);
        var first = search.search(condition);
        assertThat(first.products()).extracting(ProductSummaryResponse::getName).containsExactly("초콜릿", "초콜릿 선물");
        assertThat(first.totalElements()).isEqualTo(4);
        var second = search.search(new ProductSearchCondition(List.of(), null, "초콜릿", null, null, null, false, 1, 2));
        assertThat(second.products()).extracting(ProductSummaryResponse::getName).containsExactly("초콜렛", "디저트 세트");
        for (ProductSort sort : ProductSort.values()) {
            var cc = new ProductSearchCondition(List.of(), null, null, null, null, sort, false, 0, 100);
            var lc = new ProductSearchCondition(List.of(), null, "초콜릿", null, null, sort, false, 0, 100);
            when(products.getProducts(cc)).thenReturn(new ProductPageResponse(candidate, 0, 100, 4, 1, true, true));
            when(products.getProducts(lc)).thenReturn(empty);
            var explicit = search.search(new ProductSearchCondition(List.of(), null, "초콜릿", null, null, sort, false, 0, 4));
            assertThat(explicit.products()).extracting(ProductSummaryResponse::getName).containsExactlyElementsOf(names);
        }
    }

    @Test
    void relevanceRanksExactPartialWhitespaceTypoAndSemantic() {
        float[] same = {1, 0};
        assertThat(ProductSearchService.rank("생일 선물", "생일 선물", same, same)).isEqualTo(4);
        assertThat(ProductSearchService.rank("생일 선물", "생일 선물 세트", same, same)).isEqualTo(3);
        assertThat(ProductSearchService.rank("생일 선물", "생일선물", same, same)).isEqualTo(2);
        assertThat(ProductSearchService.rank("초콜릿", "초콜렛", same, same)).isEqualTo(2);
        assertThat(ProductSearchService.rank("이불", "침구 세트", same, same)).isEqualTo(1);
        assertThat(ProductSearchService.rank("이불", "케이크", same, new float[]{0, 1})).isZero();
    }

    @Test
    void semanticMatchUsesExistingOrderAndCalculatesPageFromMatchedSet() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:search_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table categories(id bigint primary key, name varchar(100))");
        jdbc.execute("create table products(id bigint primary key, version bigint, category_id bigint, name varchar(200), summary varchar(500), description clob)");
        jdbc.execute("create table product_embeddings(product_id bigint, model varchar(100), dimensions int, product_version bigint, embedding blob, updated_at timestamp, source_hash varchar(64))");
        jdbc.execute("create table gift_occasions(id bigint primary key, name varchar(100), active boolean)");
        jdbc.execute("create table product_gift_occasions(product_id bigint, gift_occasion_id bigint)");
        jdbc.update("insert into categories values (1, '침구')");
        jdbc.update("insert into products values (1, 0, 1, '침구 세트', null, null), (2, 0, 1, '케이크', null, null)");
        float[] matching = new float[128]; matching[0] = 1;
        float[] unrelated = new float[128]; unrelated[1] = 1;
        String hash1 = ProductEmbeddingIndexer.hash(ProductEmbeddingIndexer.completeText(ProductEmbeddingIndexer.baseText("침구 세트", "침구", null, null), List.of()));
        String hash2 = ProductEmbeddingIndexer.hash(ProductEmbeddingIndexer.completeText(ProductEmbeddingIndexer.baseText("케이크", "침구", null, null), List.of()));
        jdbc.update("insert into product_embeddings values (1, ?, 128, 0, ?, current_timestamp, ?)", ProductEmbeddingIndexer.MODEL, ProductEmbeddingIndexer.encode(matching), hash1);
        jdbc.update("insert into product_embeddings values (2, ?, 128, 0, ?, current_timestamp, ?)", ProductEmbeddingIndexer.MODEL, ProductEmbeddingIndexer.encode(unrelated), hash2);
        var products = mock(ProductService.class);
        var embedding = mock(EmbeddingService.class);
        var condition = new ProductSearchCondition(List.of(3L), null, "이불", 10L, 500L, ProductSort.PRICE_ASC, true, 0, 1);
        var candidateCondition = new ProductSearchCondition(List.of(3L), null, null, 10L, 500L, ProductSort.PRICE_ASC, true, 0, 100);
        var legacyCondition = new ProductSearchCondition(List.of(3L), null, "이불", 10L, 500L, ProductSort.PRICE_ASC, true, 0, 100);
        var one = ProductSummaryResponse.builder().id(1L).name("침구 세트").build();
        var two = ProductSummaryResponse.builder().id(2L).name("케이크").build();
        when(products.getProducts(candidateCondition)).thenReturn(new ProductPageResponse(List.of(one, two), 0, 100, 2, 1, true, true));
        when(products.getProducts(legacyCondition)).thenReturn(new ProductPageResponse(List.of(), 0, 100, 0, 0, true, true));
        when(embedding.embed("이불", EmbeddingService.Task.RETRIEVAL_QUERY)).thenReturn(Optional.of(matching));

        var result = new ProductSearchService(products, embedding, new NamedParameterJdbcTemplate(ds), true, 128, 100).search(condition);
        assertThat(result.products()).containsExactly(one);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        verify(products).getProducts(candidateCondition);
        var fallback = new ProductPageResponse(List.of(), 0, 1, 0, 0, true, true);
        when(products.getProducts(condition)).thenReturn(fallback);
        jdbc.update("update categories set name = '변경' where id = 1");
        assertThat(new ProductSearchService(products, embedding, new NamedParameterJdbcTemplate(ds), true, 128, 100).search(condition)).isSameAs(fallback);
        jdbc.update("update categories set name = '침구' where id = 1");
        jdbc.update("insert into gift_occasions values (1, '생일', true)");
        jdbc.update("insert into product_gift_occasions values (1, 1)");
        assertThat(new ProductSearchService(products, embedding, new NamedParameterJdbcTemplate(ds), true, 128, 100).search(condition)).isSameAs(fallback);
        String updatedHash = ProductEmbeddingIndexer.hash(ProductEmbeddingIndexer.completeText(
                ProductEmbeddingIndexer.baseText("침구 세트", "침구", null, null), List.of("생일")));
        jdbc.update("update product_embeddings set source_hash = ? where product_id = 1", updatedHash);
        assertThat(new ProductSearchService(products, embedding, new NamedParameterJdbcTemplate(ds), true, 128, 100).search(condition).products()).containsExactly(one);
        jdbc.update("update gift_occasions set name = '기념일' where id = 1");
        assertThat(new ProductSearchService(products, embedding, new NamedParameterJdbcTemplate(ds), true, 128, 100).search(condition)).isSameAs(fallback);
    }

    @Test
    void missingEmbeddingTableFallsBackToOriginalSearch() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:missing_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var products = mock(ProductService.class);
        var condition = new ProductSearchCondition(List.of(), null, "선물", null, null, null, false, 0, 20);
        var candidateCondition = new ProductSearchCondition(List.of(), null, null, null, null, null, false, 0, 100);
        var item = ProductSummaryResponse.builder().id(1L).name("선물").build();
        var fallback = new ProductPageResponse(List.of(item), 0, 20, 1, 1, true, true);
        when(products.getProducts(candidateCondition)).thenReturn(new ProductPageResponse(List.of(item), 0, 100, 1, 1, true, true));
        when(products.getProducts(condition)).thenReturn(fallback);
        assertThat(new ProductSearchService(products, mock(EmbeddingService.class), new NamedParameterJdbcTemplate(ds), true, 128, 100).search(condition)).isSameAs(fallback);
    }

    @Test
    void preservesConditionAndResponse() {
        ProductService products = mock(ProductService.class);
        var condition = new ProductSearchCondition(List.of(3L), 4L, "선물", 100L, 500L,
                ProductSort.POPULAR, true, 2, 10);
        var response = new ProductPageResponse(List.of(), 2, 10, 27, 3, false, true);
        when(products.getProducts(condition)).thenReturn(response);

        assertThat(new ProductSearchService(products, mock(EmbeddingService.class),
                mock(NamedParameterJdbcTemplate.class), false, 128, 100).search(condition)).isSameAs(response);
        verify(products).getProducts(condition);
    }
}
