package com.giftmarket.product.search;

import com.giftmarket.product.dto.request.ProductSearchCondition;
import com.giftmarket.product.dto.response.ProductPageResponse;
import com.giftmarket.product.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Optional;
import com.giftmarket.product.dto.response.ProductSummaryResponse;

/** MySQL stays the default and failure path. */
@Service
public class ProductSearchService {
    private final ProductService products;
    private final MeiliProperties config;
    private final MeiliClient client;
    private final SearchProductReader reader;
    private final MeiliIndexService index;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProductSearchService.class);

    @Autowired
    public ProductSearchService(ProductService products, MeiliProperties config, MeiliClient client,
            SearchProductReader reader, MeiliIndexService index) {
        this.products = products; this.config = config; this.client = client; this.reader = reader; this.index = index;
    }

    public ProductPageResponse search(ProductSearchCondition condition) {
        return indexed(condition, false).orElseGet(() -> products.getProducts(condition));
    }

    public Optional<List<ProductSummaryResponse>> suggestions(String query) {
        return indexed(new ProductSearchCondition(List.of(), null, query, null, null, null, true, 0, 5), true)
                .map(ProductPageResponse::products);
    }

    private Optional<ProductPageResponse> indexed(ProductSearchCondition condition, boolean suggestions) {
        if (!config.enabled() || !index.ready() || condition.normalizedKeyword() == null
                || condition.normalizedKeyword().length() > 100 || condition.normalizedPage() < 0
                || condition.normalizedSize() < 1 || condition.normalizedSize() > 100
                || (condition.minPrice() != null && condition.maxPrice() != null && condition.minPrice() > condition.maxPrice()))
            return Optional.empty();
        long revision = index.revision();
        try {
            var ids = client.search(condition.normalizedKeyword());
            var result = reader.read(ids, condition, suggestions);
            if (!index.ready() || revision != index.revision()) return Optional.empty();
            return Optional.of(result);
        } catch (MeiliClient.CapacityExceeded ex) {
            log.debug("Meilisearch SQL fallback reason=complete-match-limit");
            return Optional.empty();
        } catch (RuntimeException ex) {
            index.rebuild();
            log.warn("Meilisearch SQL fallback type={} status={}", ex.getClass().getSimpleName(),
                    ex instanceof org.springframework.web.client.RestClientResponseException response ? response.getStatusCode().value() : 0);
            return Optional.empty();
        }
    }
}
