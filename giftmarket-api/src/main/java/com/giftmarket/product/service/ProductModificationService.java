package com.giftmarket.product.service;

import com.giftmarket.product.draft.dto.response.ProductDraftResponse;
import com.giftmarket.product.draft.service.ProductDraftService;
import com.giftmarket.product.dto.request.ProductModificationRequest;
import com.giftmarket.product.dto.request.ProductModificationVariantRequest;
import com.giftmarket.product.dto.request.ProductOptionReferenceRequest;
import com.giftmarket.product.dto.request.ProductVariantRequest;
import com.giftmarket.product.dto.request.ProductVariantUpdateRequest;
import com.giftmarket.product.dto.response.ProductOptionGroupResponse;
import com.giftmarket.product.dto.response.ProductOptionResponse;
import com.giftmarket.product.dto.response.ProductOptionValueResponse;
import com.giftmarket.product.dto.response.ProductResponse;
import com.giftmarket.product.exception.ProductException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductModificationService {

    private final ProductService productService;
    private final ProductOptionService productOptionService;
    private final ProductVariantService productVariantService;
    private final ProductDraftService productDraftService;

    @Transactional
    public ProductResponse modifyProduct(
            Long userId,
            Long productId,
            ProductModificationRequest request
    ) {
        validateDraft(
                userId,
                productId,
                request.draftId()
        );

        /*
         * 상품 기본정보 수정.
         * 현재 상품의 판매 상태는 그대로 유지합니다.
         */
        productService.updateProduct(
                userId,
                productId,
                request.product()
        );

        /*
         * 옵션 저장 후 새로 생성된 옵션값까지 포함한
         * 실제 DB ID를 응답으로 확보합니다.
         */
        productOptionService.retireVariantsUsingRemovedOptions(
                userId,
                productId,
                request.options()
        );

        ProductOptionResponse savedOptions =
                productOptionService.updateProductOptions(
                        userId,
                        productId,
                        request.options()
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
        } else {
            if (!request.normalizedVariants().isEmpty()) {
                throw new ProductException(
                        "옵션이 없는 상품에는 SKU를 등록할 수 없습니다."
                );
            }

            productVariantService.updateProductVariants(
                    userId,
                    productId,
                    new ProductVariantUpdateRequest(List.of())
            );
        }

        /*
         * Variant 저장으로 Product 재고가 변경될 수 있으므로
         * 최종 상태를 다시 조회합니다.
         */
        ProductResponse finalProduct =
                productService.getMyProduct(
                        userId,
                        productId
                );

        if (request.draftId() != null) {
            productDraftService.deleteDraft(
                    userId,
                    request.draftId()
            );
        }

        return finalProduct;
    }

    private void validateDraft(
            Long userId,
            Long productId,
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

        if (draft.productId() == null) {
            throw new ProductException(
                    "신규 상품 임시저장 데이터는 상품 수정에 사용할 수 없습니다."
            );
        }

        if (!draft.productId().equals(productId)) {
            throw new ProductException(
                    "다른 상품의 임시저장 데이터는 사용할 수 없습니다."
            );
        }
    }

    private ProductVariantUpdateRequest createVariantUpdateRequest(
            List<ProductModificationVariantRequest> modificationVariants,
            ProductOptionResponse savedOptions
    ) {
        Map<OptionReferenceKey, Long> optionValueIdMap =
                createOptionValueIdMap(
                        savedOptions
                );

        List<ProductVariantRequest> variants =
                modificationVariants.stream()
                        .map(
                                variant ->
                                        createVariantRequest(
                                                variant,
                                                optionValueIdMap
                                        )
                        )
                        .toList();

        return new ProductVariantUpdateRequest(
                variants
        );
    }

    private ProductVariantRequest createVariantRequest(
            ProductModificationVariantRequest request,
            Map<OptionReferenceKey, Long> optionValueIdMap
    ) {
        List<Long> optionValueIds =
                request.normalizedOptions()
                        .stream()
                        .map(
                                reference ->
                                        resolveOptionValueId(
                                                reference,
                                                optionValueIdMap
                                        )
                        )
                        .toList();

        return new ProductVariantRequest(
                request.id(),
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
                            "상품 옵션 순서가 중복되어 SKU를 저장할 수 없습니다."
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
