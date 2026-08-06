package com.giftmarket.product.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.product.dto.response.ProductDetailResponse;
import com.giftmarket.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> getProduct(
            @PathVariable Long productId
    ) {
        return ApiResponse.success(
                productService.getProduct(productId)
        );
    }
}