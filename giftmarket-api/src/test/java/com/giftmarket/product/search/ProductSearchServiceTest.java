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
