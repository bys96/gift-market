package com.giftmarket.product.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.product.dto.request.ProductSearchCondition;
import com.giftmarket.product.dto.response.ProductDetailResponse;
import com.giftmarket.product.dto.response.HomeProductResponse;
import com.giftmarket.product.dto.response.ProductPageResponse;
import com.giftmarket.product.service.ProductService;
import com.giftmarket.product.service.ProductSuggestionService;
import com.giftmarket.product.dto.response.ProductSuggestionsResponse;
import com.giftmarket.product.search.ProductSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;
    private final ProductSuggestionService productSuggestionService;

    @GetMapping("/suggest")
    public ApiResponse<ProductSuggestionsResponse> suggest(@RequestParam(required = false) String q) {
        return ApiResponse.success(productSuggestionService.suggest(q));
    }

    @GetMapping
    public ApiResponse<ProductPageResponse> getProducts(
            @Valid @ModelAttribute ProductSearchCondition condition
    ) {
        return ApiResponse.success(
                productSearchService.search(condition)
        );
    }

    @GetMapping("/home")
    public ApiResponse<HomeProductResponse> getHomeProducts() {
        return ApiResponse.success(productService.getHomeProducts());
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> getProduct(
            @PathVariable Long productId
    ) {
        return ApiResponse.success(
                productService.getProduct(productId)
        );
    }
}
