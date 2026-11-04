package com.giftmarket.product.search;

import com.giftmarket.product.dto.request.ProductSearchCondition;
import com.giftmarket.product.dto.response.ProductPageResponse;
import com.giftmarket.product.service.ProductService;
import org.springframework.stereotype.Service;

/** Uses the existing database keyword query, filters, ordering and pagination. */
@Service
public class ProductSearchService {
    private final ProductService products;

    public ProductSearchService(ProductService products) {
        this.products = products;
    }

    public ProductPageResponse search(ProductSearchCondition condition) {
        return products.getProducts(condition);
    }
}
