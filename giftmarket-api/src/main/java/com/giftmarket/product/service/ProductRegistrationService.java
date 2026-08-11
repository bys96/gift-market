package com.giftmarket.product.service;

import com.giftmarket.product.draft.dto.response.ProductDraftResponse;
import com.giftmarket.product.draft.service.ProductDraftService;
import com.giftmarket.product.dto.request.ProductCreateRequest;
import com.giftmarket.product.dto.request.ProductOptionReferenceRequest;
import com.giftmarket.product.dto.request.ProductOptionUpdateRequest;
import com.giftmarket.product.dto.request.ProductRegistrationRequest;
import com.giftmarket.product.dto.request.ProductRegistrationVariantRequest;
import com.giftmarket.product.dto.request.ProductStatusUpdateRequest;
import com.giftmarket.product.dto.request.ProductVariantRequest;
import com.giftmarket.product.dto.request.ProductVariantUpdateRequest;
import com.giftmarket.product.dto.response.ProductOptionGroupResponse;
import com.giftmarket.product.dto.response.ProductOptionResponse;
import com.giftmarket.product.dto.response.ProductOptionValueResponse;
import com.giftmarket.product.dto.response.ProductRegistrationResponse;
import com.giftmarket.product.dto.response.ProductResponse;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.exception.ProductException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductRegistrationService {

    private final ProductService productService;
    private final ProductOptionService productOptionService;
    private final ProductVariantService productVariantService;
    private final ProductDraftService productDraftService;

    @Transactional
    public ProductRegistrationResponse registerProduct(
            Long userId,
            ProductRegistrationRequest request
    ) {
        validateDraft(
                userId,
                request.draftId()
        );

        ProductCreateRequest productRequest =
                createDraftProductRequest(
                        request.product()
                );

        /*
         * Product는 먼저 실제 테이블에 생성하지만
         * 판매 상태는 DRAFT로 유지합니다.
         *
         * 아래 Option / Variant 저장 중 하나라도 실패하면
         * 이 메서드 전체가 rollback 됩니다.
         */
        ProductResponse createdProduct =
                productService.createProduct(
                        userId,
                        productRequest
                );

        Long productId =
                createdProduct.getId();

        ProductOptionUpdateRequest optionRequest =
                request.options();

        ProductOptionResponse savedOptions =
                productOptionService.updateProductOptions(
                        userId,
                        productId,
                        optionRequest
                );

        boolean hasOptions =
                !savedOptions.getOptionGroups().isEmpty();

        if (hasOptions) {
            if (request.normalizedVariants().isEmpty()) {
                throw new ProductException(
                        "옵션 상품은 SKU를 1개 이상 등록해야 합니다."
                );
            }

            ProductVariantUpdateRequest variantRequest =
                    createVariantUpdateRequest(
                            request.normalizedVariants(),
                            savedOptions
                    );

            productVariantService.updateProductVariants(
                    userId,
                    productId,
                    variantRequest
            );
        } else if (!request.normalizedVariants().isEmpty()) {
            throw new ProductException(
                    "옵션이 없는 상품에는 SKU를 등록할 수 없습니다."
            );
        }

        /*
         * 옵션 상품이면 ProductVariantService에서
         * 활성 Variant 재고 합계가 Product.stockQuantity에
         * 이미 동기화된 상태입니다.
         *
         * 일반 상품이면 ProductCreateRequest의
         * stockQuantity가 그대로 유지됩니다.
         */
        ProductResponse finalProduct =
                productService.updateProductStatus(
                        userId,
                        productId,
                        new ProductStatusUpdateRequest(
                                ProductStatus.ON_SALE
                        )
                );

        if (request.draftId() != null) {
            productDraftService.deleteDraft(
                    userId,
                    request.draftId()
            );
        }

        return ProductRegistrationResponse.from(
                finalProduct
        );
    }

    private void validateDraft(
            Long userId,
            Long draftId
    ) {
        if (draftId == null) {
            return;
        }

        ProductDraftResponse draft =
                productDraftService.getDraft(
                        userId,
                        draftId
                );

        /*
         * 신규 상품 등록 API이므로
         * 기존 상품 수정 Draft는 사용할 수 없습니다.
         */
        if (draft.productId() != null) {
            throw new ProductException(
                    "기존 상품 수정 임시저장 데이터는 신규 상품 등록에 사용할 수 없습니다."
            );
        }
    }

    private ProductCreateRequest createDraftProductRequest(
            ProductCreateRequest request
    ) {
        /*
         * 클라이언트가 startSale=true를 보내더라도
         * 여기서는 무조건 false.
         *
         * Option / Variant까지 전부 성공한 뒤
         * 마지막에 ON_SALE로 전환합니다.
         */
        return new ProductCreateRequest(
                request.categoryId(),
                request.name(),
                request.brandName(),
                request.summary(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                request.representativeImageKey(),
                request.galleryImageKeys(),
                request.freeShipping(),
                request.shippingFee(),
                request.shippingPreparationDays(),
                request.returnShippingFee(),
                request.exchangeShippingFee(),
                false
        );
    }

    private ProductVariantUpdateRequest createVariantUpdateRequest(
            List<ProductRegistrationVariantRequest> registrationVariants,
            ProductOptionResponse savedOptions
    ) {
        Map<OptionReferenceKey, Long> optionValueIdMap =
                createOptionValueIdMap(
                        savedOptions
                );

        List<ProductVariantRequest> variants =
                registrationVariants.stream()
                        .map(
                                registrationVariant ->
                                        createVariantRequest(
                                                registrationVariant,
                                                optionValueIdMap
                                        )
                        )
                        .toList();

        return new ProductVariantUpdateRequest(
                variants
        );
    }

    private ProductVariantRequest createVariantRequest(
            ProductRegistrationVariantRequest request,
            Map<OptionReferenceKey, Long> optionValueIdMap
    ) {
        List<Long> optionValueIds =
                request.normalizedOptions()
                        .stream()
                        .map(
                                optionReference ->
                                        resolveOptionValueId(
                                                optionReference,
                                                optionValueIdMap
                                        )
                        )
                        .toList();

        return new ProductVariantRequest(
                null,
                request.skuCode(),
                optionValueIds,
                request.additionalPrice(),
                request.stockQuantity(),
                request.active()
        );
    }

    private Map<OptionReferenceKey, Long> createOptionValueIdMap(
            ProductOptionResponse savedOptions
    ) {
        Map<OptionReferenceKey, Long> result =
                new HashMap<>();

        for (
                ProductOptionGroupResponse group :
                savedOptions.getOptionGroups()
        ) {
            for (
                    ProductOptionValueResponse value :
                    group.getValues()
            ) {
                OptionReferenceKey key =
                        new OptionReferenceKey(
                                group.getSortOrder(),
                                value.getSortOrder()
                        );

                Long previous =
                        result.put(
                                key,
                                value.getId()
                        );

                if (previous != null) {
                    throw new ProductException(
                            "상품 옵션 순서가 중복되어 SKU를 생성할 수 없습니다."
                    );
                }
            }
        }

        return result;
    }

    private Long resolveOptionValueId(
            ProductOptionReferenceRequest reference,
            Map<OptionReferenceKey, Long> optionValueIdMap
    ) {
        OptionReferenceKey key =
                new OptionReferenceKey(
                        reference.optionGroupSortOrder(),
                        reference.optionValueSortOrder()
                );

        Long optionValueId =
                optionValueIdMap.get(key);

        if (optionValueId == null) {
            throw new ProductException(
                    "SKU가 참조하는 상품 옵션을 찾을 수 없습니다."
            );
        }

        return optionValueId;
    }

    private record OptionReferenceKey(
            Integer optionGroupSortOrder,
            Integer optionValueSortOrder
    ) {
    }
}