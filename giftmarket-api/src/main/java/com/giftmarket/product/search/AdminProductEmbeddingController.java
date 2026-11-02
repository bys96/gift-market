package com.giftmarket.product.search;

import com.giftmarket.global.response.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/product-search")
public class AdminProductEmbeddingController {
    private final ProductEmbeddingIndexer indexer;

    public AdminProductEmbeddingController(ProductEmbeddingIndexer indexer) { this.indexer = indexer; }

    @PostMapping("/reindex")
    public ApiResponse<ProductEmbeddingIndexer.Result> reindex(
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "1") int limit) {
        return ApiResponse.success(indexer.reindex(afterId, limit));
    }
}
