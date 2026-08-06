package com.giftmarket.global.storage.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.global.storage.dto.PresignedUrlRequest;
import com.giftmarket.global.storage.dto.PresignedUrlResponse;
import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.global.storage.type.StorageType;
import com.giftmarket.product.exception.ProductException;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final SellerRepository sellerRepository;

    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> createPresignedUrl(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PresignedUrlRequest request
    ) {
        Long ownerId = resolveOwnerId(
                userId,
                request.type()
        );

        return ApiResponse.success(
                storageService.createPresignedUrl(
                        ownerId,
                        request
                )
        );
    }

    private Long resolveOwnerId(
            Long userId,
            StorageType storageType
    ) {
        if (!isProductStorageType(storageType)) {
            return userId;
        }

        Seller seller = sellerRepository.findByUserId(userId)
                .orElseThrow(() -> new ProductException(
                        "판매자 정보를 찾을 수 없습니다."
                ));

        if (seller.getStatus() != SellerStatus.ACTIVE) {
            throw new ProductException(
                    "활성 상태의 판매자만 상품 이미지를 업로드할 수 있습니다."
            );
        }

        return seller.getId();
    }

    private boolean isProductStorageType(StorageType storageType) {
        return storageType
                == StorageType.PRODUCT_REPRESENTATIVE
                || storageType == StorageType.PRODUCT_GALLERY
                || storageType == StorageType.PRODUCT_CONTENT;
    }
}