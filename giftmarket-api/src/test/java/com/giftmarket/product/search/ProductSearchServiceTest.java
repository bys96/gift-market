package com.giftmarket.product.search;

import com.giftmarket.product.dto.request.ProductSearchCondition;
import com.giftmarket.product.dto.response.ProductPageResponse;
import com.giftmarket.product.entity.ProductSort;
import com.giftmarket.product.service.ProductService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductSearchServiceTest {
    @Test void fallbackSearchesRemainIndependentOfKeywordHistory() {
        var config = new MeiliProperties(); config.setEngine("meilisearch");
        var products = mock(ProductService.class);
        var client = mock(MeiliClient.class);
        var reader = mock(SearchProductReader.class);
        var index = mock(MeiliIndexService.class);
        var search = new ProductSearchService(products, config, client, reader, index);
        for (String keyword : List.of("침구", "침구류", "침구", "침구류", "침구")) {
            var condition = new ProductSearchCondition(List.of(), null, keyword, null, null, null, false, 0, 20);
            var result = new ProductPageResponse(List.of(), 0, 20, 0, 0, true, true);
            when(products.getProducts(condition)).thenReturn(result);
            assertThat(search.search(condition)).isSameAs(result);
            assertThat(search.suggestions(keyword)).isEmpty();
        }
        verifyNoInteractions(client, reader);
    }
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void zeroHitsDistinguishProviderNoMatchFromDatabaseFilteringWithoutFallback(boolean hasCandidates) {
        var config = new MeiliProperties(); config.setEngine("meilisearch");
        var products = mock(ProductService.class);
        var client = mock(MeiliClient.class);
        var reader = mock(SearchProductReader.class);
        var index = mock(MeiliIndexService.class);
        when(index.ready()).thenReturn(true);
        var condition = new ProductSearchCondition(List.of(), null, "이불", null, null, null, true, 0, 20);
        var candidates = hasCandidates ? List.of(1L) : List.<Long>of();
        var empty = new ProductPageResponse(List.of(), 0, 20, 0, 0, true, true);
        when(client.search("이불")).thenReturn(candidates);
        when(reader.read(candidates, condition, false)).thenReturn(empty);
        var search = new ProductSearchService(products, config, client, reader, index);
        assertThat(search.search(condition)).isSameAs(empty);
        verify(reader).read(candidates, condition, false);
        verifyNoInteractions(products);
        verify(index, never()).rebuild();
    }

    @Test void unreadyIndexUsesMysqlAndDoesNotPretendEngineReturnedZeroHits() {
        var config = new MeiliProperties(); config.setEngine("meilisearch");
        var products = mock(ProductService.class);
        var client = mock(MeiliClient.class);
        var reader = mock(SearchProductReader.class);
        var index = mock(MeiliIndexService.class);
        var condition = new ProductSearchCondition(List.of(), null, "이불", null, null, null, true, 0, 20);
        var empty = new ProductPageResponse(List.of(), 0, 20, 0, 0, true, true);
        when(products.getProducts(condition)).thenReturn(empty);
        assertThat(new ProductSearchService(products, config, client, reader, index).search(condition)).isSameAs(empty);
        verify(products).getProducts(condition);
        verifyNoInteractions(client, reader);
    }
    @Test
    void enabledSearchHydratesIdsAndFallsBackOnFailureOrConcurrentCommit() {
        var products = mock(ProductService.class);
        var config = new MeiliProperties(); config.setEngine("meilisearch");
        var client = mock(MeiliClient.class);
        var reader = mock(SearchProductReader.class);
        var index = mock(MeiliIndexService.class);
        when(index.ready()).thenReturn(true);
        var search = new ProductSearchService(products, config, client, reader, index);
        var condition = new ProductSearchCondition(List.of(3L), null, "bedding", 100L, 500L, ProductSort.PRICE_ASC, true, 1, 2);
        var result = new ProductPageResponse(List.of(), 1, 2, 0, 0, false, true);
        doReturn(List.of(4L, 5L)).when(client).search("bedding");
        when(reader.read(List.of(4L, 5L), condition, false)).thenReturn(result);
        assertThat(search.search(condition)).isSameAs(result);
        verifyNoInteractions(products);
        when(client.search("bedding")).thenThrow(new IllegalStateException("unavailable"));
        when(products.getProducts(condition)).thenReturn(result);
        assertThat(search.search(condition)).isSameAs(result);
        verify(products).getProducts(condition);
        doReturn(List.of(4L, 5L)).when(client).search("bedding");
        when(index.revision()).thenReturn(1L, 2L);
        assertThat(search.search(condition)).isSameAs(result);
        verify(products, times(2)).getProducts(condition);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void delegatesAllKeywordsFiltersSortsAndPagesWithoutExternalProvider(boolean indexingEnabled) {
        var products = mock(ProductService.class);
        var config = new MeiliProperties(); config.setIndexingEnabled(indexingEnabled);
        var client = mock(MeiliClient.class);
        var reader = mock(SearchProductReader.class);
        var index = mock(MeiliIndexService.class);
        var search = new ProductSearchService(products, config, client, reader, index);
        for (String keyword : new String[]{null, "gift", "gift set", "missing"}) {
            for (ProductSort sort : ProductSort.values()) {
                var condition = new ProductSearchCondition(List.of(3L), 4L, keyword, 100L, 500L,
                        sort, true, 2, 10);
                var result = new ProductPageResponse(List.of(), 2, 10, 0, 0, false, true);
                when(products.getProducts(condition)).thenReturn(result);
                assertThat(search.search(condition)).isSameAs(result);
                verify(products).getProducts(condition);
                clearInvocations(products);
            }
        }
        assertThat(search.suggestions("gift")).isEmpty();
        verifyNoInteractions(client, reader, index);
    }
}
