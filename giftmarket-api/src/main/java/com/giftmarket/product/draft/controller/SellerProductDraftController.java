package com.giftmarket.product.draft.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.product.draft.dto.request.ProductDraftCreateRequest;
import com.giftmarket.product.draft.dto.request.ProductDraftUpdateRequest;
import com.giftmarket.product.draft.dto.response.ProductDraftResponse;
import com.giftmarket.product.draft.service.ProductDraftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller/product-drafts")
public class SellerProductDraftController {

    private final ProductDraftService productDraftService;

    @PostMapping
    public ApiResponse<ProductDraftResponse> createDraft(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProductDraftCreateRequest request
    ) {
        return ApiResponse.success(
                productDraftService.createDraft(
                        userId,
                        request
                )
        );
    }

    @GetMapping("/{draftId}")
    public ApiResponse<ProductDraftResponse> getDraft(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long draftId
    ) {
        return ApiResponse.success(
                productDraftService.getDraft(
                        userId,
                        draftId
                )
        );
    }

    @GetMapping
    public ApiResponse<?> getDrafts(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long productId,
            @RequestParam(
                    required = false,
                    defaultValue = "false"
            ) boolean newOnly
    ) {
        if (productId != null) {
            return ApiResponse.success(
                    productDraftService.getProductDraft(
                            userId,
                            productId
                    )
            );
        }

        if (newOnly) {
            return ApiResponse.success(
                    productDraftService.getMyNewProductDrafts(
                            userId
                    )
            );
        }

        return ApiResponse.success(
                productDraftService.getMyDrafts(
                        userId
                )
        );
    }

    @PutMapping("/{draftId}")
    public ApiResponse<ProductDraftResponse> updateDraft(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long draftId,
            @Valid @RequestBody ProductDraftUpdateRequest request
    ) {
        return ApiResponse.success(
                productDraftService.updateDraft(
                        userId,
                        draftId,
                        request
                )
        );
    }

    @DeleteMapping("/{draftId}")
    public ApiResponse<Void> deleteDraft(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long draftId
    ) {
        productDraftService.deleteDraft(
                userId,
                draftId
        );

        return ApiResponse.success(null);
    }
}