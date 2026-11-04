package com.giftmarket.product.search;

import com.giftmarket.product.dto.request.ProductSearchCondition;
import com.giftmarket.product.dto.response.ProductPageResponse;
import com.giftmarket.product.entity.ProductSort;
import com.giftmarket.product.service.ProductService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductSearchServiceTest {
    @Test
    void delegatesAllKeywordsFiltersSortsAndPagesWithoutExternalProvider() {
        var products = mock(ProductService.class);
        var search = new ProductSearchService(products);
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
    }
}
