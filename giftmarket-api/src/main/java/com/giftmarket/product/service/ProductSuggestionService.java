package com.giftmarket.product.service;

import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.product.dto.response.ProductSuggestionsResponse;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.repository.ProductRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductSuggestionService {
    private final ProductRepository products;
    private final StorageService storage;
    private final com.giftmarket.product.search.ProductSearchService search;

    public ProductSuggestionService(ProductRepository products, StorageService storage) {
        this(products, storage, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ProductSuggestionService(ProductRepository products, StorageService storage,
            com.giftmarket.product.search.ProductSearchService search) {
        this.products = products;
        this.storage = storage;
        this.search = search;
    }

    @Transactional(readOnly = true)
    public ProductSuggestionsResponse suggest(String query) {
        if (query == null || query.isBlank() || query.trim().length() > 100) {
            return new ProductSuggestionsResponse(List.of(), List.of());
        }
        if (search != null) {
            var indexed = search.suggestions(query);
            if (indexed.isPresent()) {
                var found = indexed.get();
                return new ProductSuggestionsResponse(found.stream().map(product ->
                        new ProductSuggestionsResponse.Item(product.getId(), product.getName(),
                                imageUrl(product.getRepresentativeImageKey()), product.getPrice())).toList(),
                        found.stream().map(com.giftmarket.product.dto.response.ProductSummaryResponse::getName).distinct().limit(5).toList());
            }
        }
        String escaped = query.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        List<Product> found = products.findSuggestionProducts(escaped, PageRequest.of(0, 5));
        List<ProductSuggestionsResponse.Item> items = found.stream().map(product ->
                new ProductSuggestionsResponse.Item(product.getId(), product.getName(),
                        imageUrl(product.getRepresentativeImageKey()), product.getPrice())).toList();
        return new ProductSuggestionsResponse(items,
                found.stream().map(Product::getName).distinct().limit(5).toList());
    }

    private String imageUrl(String key) {
        if (key == null || key.isBlank()) return null;
        if (key.startsWith("https://") || key.startsWith("http://")) return key;
        try {
            return storage.createReadUrl(key);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
