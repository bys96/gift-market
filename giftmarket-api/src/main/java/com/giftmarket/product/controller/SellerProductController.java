package com.giftmarket.product.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.product.dto.request.ProductCreateRequest;
import com.giftmarket.product.dto.request.ProductStatusUpdateRequest;
import com.giftmarket.product.dto.request.ProductStockUpdateRequest;
import com.giftmarket.product.dto.request.ProductUpdateRequest;
import com.giftmarket.product.dto.response.ProductPageResponse;
import com.giftmarket.product.dto.response.ProductResponse;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller/products")
public class SellerProductController {

    private final ProductService productService;

    @PostMapping
    public ApiResponse<ProductResponse> createProduct(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        return ApiResponse.success(
                productService.createProduct(
                        userId,
                        request
                )
        );
    }

    @GetMapping
    public ApiResponse<ProductPageResponse> getMyProducts(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                productService.getMyProducts(
                        userId,
                        status,
                        page,
                        size
                )
        );
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> getMyProduct(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId
    ) {
        return ApiResponse.success(
                productService.getMyProduct(
                        userId,
                        productId
                )
        );
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductResponse> updateProduct(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        return ApiResponse.success(
                productService.updateProduct(
                        userId,
                        productId,
                        request
                )
        );
    }

    @PatchMapping("/{productId}/status")
    public ApiResponse<ProductResponse> updateProductStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductStatusUpdateRequest request
    ) {
        return ApiResponse.success(
                productService.updateProductStatus(
                        userId,
                        productId,
                        request
                )
        );
    }

    @PatchMapping("/{productId}/stock")
    public ApiResponse<ProductResponse> updateProductStock(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductStockUpdateRequest request
    ) {
        return ApiResponse.success(
                productService.updateProductStock(
                        userId,
                        productId,
                        request
                )
        );
    }
}