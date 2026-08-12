package com.giftmarket.product.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.product.dto.request.ProductCreateRequest;
import com.giftmarket.product.dto.request.ProductOptionUpdateRequest;
import com.giftmarket.product.dto.request.ProductStatusUpdateRequest;
import com.giftmarket.product.dto.request.ProductStockUpdateRequest;
import com.giftmarket.product.dto.request.ProductUpdateRequest;
import com.giftmarket.product.dto.request.ProductVariantUpdateRequest;
import com.giftmarket.product.dto.request.ProductRegistrationRequest;
import com.giftmarket.product.dto.request.ProductModificationRequest;
import com.giftmarket.product.dto.response.ProductOptionResponse;
import com.giftmarket.product.dto.response.ProductResponse;
import com.giftmarket.product.dto.response.ProductVariantListResponse;
import com.giftmarket.product.dto.response.SellerProductPageResponse;
import com.giftmarket.product.dto.response.ProductRegistrationResponse;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.service.ProductOptionService;
import com.giftmarket.product.service.ProductService;
import com.giftmarket.product.service.ProductVariantService;
import com.giftmarket.product.service.ProductRegistrationService;
import com.giftmarket.product.service.ProductModificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final ProductOptionService productOptionService;
    private final ProductVariantService productVariantService;
    private final ProductRegistrationService productRegistrationService;
    private final ProductModificationService productModificationService;

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

    @PostMapping("/registration")
    public ApiResponse<ProductRegistrationResponse> registerProduct(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProductRegistrationRequest request
    ) {
        return ApiResponse.success(
                productRegistrationService.registerProduct(
                        userId,
                        request
                )
        );
    }

    @PutMapping("/{productId}/modification")
    public ApiResponse<ProductResponse> modifyProduct(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductModificationRequest request
    ) {
        return ApiResponse.success(
                productModificationService.modifyProduct(
                        userId,
                        productId,
                        request
                )
        );
    }

    @GetMapping
    public ApiResponse<SellerProductPageResponse> getMyProducts(
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

    @GetMapping("/{productId}/options")
    public ApiResponse<ProductOptionResponse> getProductOptions(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId
    ) {
        return ApiResponse.success(
                productOptionService.getProductOptions(
                        userId,
                        productId
                )
        );
    }

    @PutMapping("/{productId}/options")
    public ApiResponse<ProductOptionResponse> updateProductOptions(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductOptionUpdateRequest request
    ) {
        return ApiResponse.success(
                productOptionService.updateProductOptions(
                        userId,
                        productId,
                        request
                )
        );
    }

    @GetMapping("/{productId}/variants")
    public ApiResponse<ProductVariantListResponse> getProductVariants(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId
    ) {
        return ApiResponse.success(
                productVariantService.getProductVariants(
                        userId,
                        productId
                )
        );
    }

    @PutMapping("/{productId}/variants")
    public ApiResponse<ProductVariantListResponse> updateProductVariants(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductVariantUpdateRequest request
    ) {
        return ApiResponse.success(
                productVariantService.updateProductVariants(
                        userId,
                        productId,
                        request
                )
        );
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId
    ) {
        productService.deleteProduct(
                userId,
                productId
        );

        return ApiResponse.success(null);
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