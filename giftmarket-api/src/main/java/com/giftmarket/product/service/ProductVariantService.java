package com.giftmarket.product.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.product.dto.request.ProductVariantRequest;
import com.giftmarket.product.dto.request.ProductVariantUpdateRequest;
import com.giftmarket.product.dto.response.ProductVariantListResponse;
import com.giftmarket.product.dto.response.ProductVariantResponse;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductOptionGroup;
import com.giftmarket.product.entity.ProductOptionValue;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.entity.ProductVariantOptionValue;
import com.giftmarket.product.exception.ProductException;
import com.giftmarket.product.repository.ProductOptionGroupRepository;
import com.giftmarket.product.repository.ProductOptionValueRepository;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.product.repository.ProductVariantOptionValueRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductRepository productRepository;

    private final ProductOptionGroupRepository
            productOptionGroupRepository;

    private final ProductOptionValueRepository
            productOptionValueRepository;

    private final ProductVariantRepository
            productVariantRepository;

    private final ProductVariantOptionValueRepository
            productVariantOptionValueRepository;

    private final SellerRepository sellerRepository;

    @Transactional(readOnly = true)
    public ProductVariantListResponse getProductVariants(
            Long userId,
            Long productId
    ) {
        Seller seller = getActiveSeller(userId);

        Product product = getSellerProduct(
                productId,
                seller.getId()
        );

        return createResponse(product);
    }

    @Transactional
    public ProductVariantListResponse updateProductVariants(
            Long userId,
            Long productId,
            ProductVariantUpdateRequest request
    ) {
        Seller seller = getActiveSeller(userId);

        Product product = getSellerProduct(
                productId,
                seller.getId()
        );

        List<ProductOptionGroup> optionGroups =
                productOptionGroupRepository
                        .findAllByProductIdOrderBySortOrderAsc(
                                productId
                        );

        List<ProductVariantRequest> variantRequests =
                request.normalizedVariants();

        if (optionGroups.isEmpty()) {
            if (!variantRequests.isEmpty()) {
                throw new ProductException(
                        "옵션이 없는 상품에는 SKU 조합을 등록할 수 없습니다."
                );
            }

            deactivateAllVariants(productId);

            productVariantRepository.flush();

            product.changeStockQuantity(0);

            return createResponse(product);
        }

        Map<Long, ProductOptionGroup> optionGroupMap =
                optionGroups.stream()
                        .collect(
                                Collectors.toMap(
                                        ProductOptionGroup::getId,
                                        Function.identity()
                                )
                        );

        List<ProductOptionValue> productOptionValues =
                productOptionValueRepository
                        .findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(
                                optionGroupMap.keySet()
                        );

        Map<Long, ProductOptionValue> optionValueMap =
                productOptionValues.stream()
                        .collect(
                                Collectors.toMap(
                                        ProductOptionValue::getId,
                                        Function.identity()
                                )
                        );

        List<ProductVariant> existingVariants =
                productVariantRepository
                        .findAllByProductIdOrderByIdAsc(
                                productId
                        );

        Map<Long, ProductVariant> existingVariantMap =
                existingVariants.stream()
                        .collect(
                                Collectors.toMap(
                                        ProductVariant::getId,
                                        Function.identity()
                                )
                        );

        Map<Long, Set<Long>> existingOptionValueIdsByVariant =
                getExistingOptionValueIdsByVariant(
                        existingVariants
                );

        validateVariantRequests(
                product,
                optionGroups,
                optionValueMap,
                existingVariantMap,
                existingOptionValueIdsByVariant,
                variantRequests
        );

        Set<Long> requestedExistingVariantIds =
                new HashSet<>();

        for (ProductVariantRequest variantRequest : variantRequests) {
            List<ProductOptionValue> selectedOptionValues =
                    resolveOptionValues(
                            variantRequest.normalizedOptionValueIds(),
                            optionValueMap
                    );

            String combinationKey =
                    createCombinationKey(
                            selectedOptionValues
                    );

            String normalizedSkuCode =
                    normalizeSkuCode(
                            variantRequest.skuCode()
                    );

            if (variantRequest.id() == null) {
                createVariant(
                        product,
                        normalizedSkuCode,
                        combinationKey,
                        selectedOptionValues,
                        variantRequest
                );

                continue;
            }

            ProductVariant variant =
                    existingVariantMap.get(
                            variantRequest.id()
                    );

            if (variant == null) {
                throw new ProductException(
                        "해당 상품에 속하지 않은 SKU입니다."
                );
            }

            requestedExistingVariantIds.add(
                    variant.getId()
            );

            variant.update(
                    normalizedSkuCode,
                    variant.getCombinationKey(),
                    variantRequest.additionalPrice(),
                    variantRequest.stockQuantity()
            );

            if (variantRequest.active()) {
                variant.activate();
            } else {
                variant.deactivate();
            }
        }

        for (ProductVariant existingVariant : existingVariants) {
            if (!requestedExistingVariantIds.contains(
                    existingVariant.getId()
            )) {
                existingVariant.deactivate();
            }
        }

        productVariantRepository.flush();

        synchronizeProductStock(
                product,
                productId
        );

        return createResponse(product);
    }

    private void createVariant(
            Product product,
            String skuCode,
            String combinationKey,
            List<ProductOptionValue> selectedOptionValues,
            ProductVariantRequest request
    ) {
        ProductVariant variant =
                ProductVariant.create(
                        product,
                        skuCode,
                        combinationKey,
                        request.additionalPrice(),
                        request.stockQuantity()
                );

        if (!request.active()) {
            variant.deactivate();
        }

        ProductVariant savedVariant =
                productVariantRepository.save(variant);

        List<ProductVariantOptionValue> mappings =
                selectedOptionValues.stream()
                        .map(optionValue ->
                                ProductVariantOptionValue.create(
                                        savedVariant,
                                        optionValue
                                )
                        )
                        .toList();

        productVariantOptionValueRepository.saveAll(
                mappings
        );
    }

    private void validateVariantRequests(
            Product product,
            List<ProductOptionGroup> optionGroups,
            Map<Long, ProductOptionValue> optionValueMap,
            Map<Long, ProductVariant> existingVariantMap,
            Map<Long, Set<Long>> existingOptionValueIdsByVariant,
            List<ProductVariantRequest> variantRequests
    ) {
        Set<Long> requestVariantIds =
                new HashSet<>();

        Set<String> requestSkuCodes =
                new HashSet<>();

        Set<String> requestCombinationKeys =
                new HashSet<>();

        for (ProductVariantRequest variantRequest : variantRequests) {
            if (variantRequest.id() != null
                    && !requestVariantIds.add(
                    variantRequest.id()
            )) {
                throw new ProductException(
                        "동일한 SKU를 중복 요청할 수 없습니다."
                );
            }

            String normalizedSkuCode =
                    normalizeSkuCode(
                            variantRequest.skuCode()
                    );

            if (!requestSkuCodes.add(
                    normalizedSkuCode
            )) {
                throw new ProductException(
                        "SKU 코드는 중복될 수 없습니다."
                );
            }

            validateSkuCodeAvailability(
                    product.getId(),
                    variantRequest.id(),
                    normalizedSkuCode
            );

            List<Long> optionValueIds =
                    variantRequest.normalizedOptionValueIds();

            if (new HashSet<>(optionValueIds).size()
                    != optionValueIds.size()) {
                throw new ProductException(
                        "하나의 SKU에서 동일한 옵션 값을 중복 선택할 수 없습니다."
                );
            }

            List<ProductOptionValue> selectedOptionValues =
                    resolveOptionValues(
                            optionValueIds,
                            optionValueMap
                    );

            validateOptionCombination(
                    optionGroups,
                    selectedOptionValues
            );

            String combinationKey =
                    createCombinationKey(
                            selectedOptionValues
                    );

            if (!requestCombinationKeys.add(
                    combinationKey
            )) {
                throw new ProductException(
                        "동일한 옵션 조합을 중복 등록할 수 없습니다."
                );
            }

            if (variantRequest.id() != null) {
                ProductVariant existingVariant =
                        existingVariantMap.get(
                                variantRequest.id()
                        );

                if (existingVariant == null) {
                    throw new ProductException(
                            "해당 상품에 속하지 않은 SKU입니다."
                    );
                }

                Set<Long> requestedOptionValueIds =
                        new HashSet<>(optionValueIds);

                Set<Long> existingOptionValueIds =
                        existingOptionValueIdsByVariant
                                .getOrDefault(
                                        existingVariant.getId(),
                                        Set.of()
                                );

                if (!existingOptionValueIds.equals(
                        requestedOptionValueIds
                )) {
                    throw new ProductException(
                            "기존 SKU의 옵션 조합은 변경할 수 없습니다. 기존 SKU를 비활성화한 후 새 SKU를 등록해주세요."
                    );
                }

                if (!existingVariant
                        .getCombinationKey()
                        .equals(combinationKey)) {
                    throw new ProductException(
                            "기존 SKU의 옵션 조합은 변경할 수 없습니다."
                    );
                }
            }
        }
    }

    private void validateSkuCodeAvailability(
            Long productId,
            Long variantId,
            String skuCode
    ) {
        productVariantRepository
                .findBySkuCode(skuCode)
                .ifPresent(existingVariant -> {
                    boolean sameVariant =
                            variantId != null
                                    && existingVariant
                                    .getId()
                                    .equals(variantId);

                    if (!sameVariant) {
                        throw new ProductException(
                                "이미 사용 중인 SKU 코드입니다."
                        );
                    }

                    if (!existingVariant
                            .getProduct()
                            .getId()
                            .equals(productId)) {
                        throw new ProductException(
                                "이미 사용 중인 SKU 코드입니다."
                        );
                    }
                });
    }

    private void validateOptionCombination(
            List<ProductOptionGroup> optionGroups,
            List<ProductOptionValue> selectedOptionValues
    ) {
        if (selectedOptionValues.size()
                != optionGroups.size()) {
            throw new ProductException(
                    "SKU는 모든 옵션에서 하나의 값을 선택해야 합니다."
            );
        }

        Set<Long> selectedGroupIds =
                selectedOptionValues.stream()
                        .map(value ->
                                value.getOptionGroup().getId()
                        )
                        .collect(Collectors.toSet());

        if (selectedGroupIds.size()
                != optionGroups.size()) {
            throw new ProductException(
                    "SKU는 각 옵션에서 하나의 값만 선택해야 합니다."
            );
        }

        Set<Long> requiredGroupIds =
                optionGroups.stream()
                        .map(ProductOptionGroup::getId)
                        .collect(Collectors.toSet());

        if (!selectedGroupIds.equals(
                requiredGroupIds
        )) {
            throw new ProductException(
                    "상품에 등록된 모든 옵션을 선택해주세요."
            );
        }
    }

    private List<ProductOptionValue> resolveOptionValues(
            List<Long> optionValueIds,
            Map<Long, ProductOptionValue> optionValueMap
    ) {
        List<ProductOptionValue> selectedOptionValues =
                new ArrayList<>();

        for (Long optionValueId : optionValueIds) {
            ProductOptionValue optionValue =
                    optionValueMap.get(optionValueId);

            if (optionValue == null) {
                throw new ProductException(
                        "해당 상품에 속하지 않은 옵션 값입니다."
                );
            }

            selectedOptionValues.add(optionValue);
        }

        return selectedOptionValues;
    }

    private String createCombinationKey(
            List<ProductOptionValue> selectedOptionValues
    ) {
        return selectedOptionValues.stream()
                .sorted(
                        Comparator
                                .comparing(
                                        (ProductOptionValue value) ->
                                                value.getOptionGroup()
                                                        .getSortOrder()
                                )
                                .thenComparing(
                                        value ->
                                                value.getOptionGroup()
                                                        .getId()
                                )
                )
                .map(value ->
                        value.getOptionGroup().getId()
                                + ":"
                                + value.getId()
                )
                .collect(
                        Collectors.joining("|")
                );
    }

    private Map<Long, Set<Long>>
    getExistingOptionValueIdsByVariant(
            List<ProductVariant> variants
    ) {
        if (variants.isEmpty()) {
            return Map.of();
        }

        List<Long> variantIds =
                variants.stream()
                        .map(ProductVariant::getId)
                        .toList();

        List<ProductVariantOptionValue> mappings =
                productVariantOptionValueRepository
                        .findAllByVariantIdIn(
                                variantIds
                        );

        Map<Long, Set<Long>> result =
                new HashMap<>();

        for (ProductVariantOptionValue mapping : mappings) {
            result.computeIfAbsent(
                            mapping.getVariant().getId(),
                            key -> new HashSet<>()
                    )
                    .add(
                            mapping.getOptionValue()
                                    .getId()
                    );
        }

        return result;
    }

    private void deactivateAllVariants(
            Long productId
    ) {
        List<ProductVariant> variants =
                productVariantRepository
                        .findAllByProductIdOrderByIdAsc(
                                productId
                        );

        variants.forEach(
                ProductVariant::deactivate
        );
    }

    private ProductVariantListResponse createResponse(
            Product product
    ) {
        List<ProductVariant> variants =
                productVariantRepository
                        .findAllByProductIdOrderByIdAsc(
                                product.getId()
                        );

        if (variants.isEmpty()) {
            return ProductVariantListResponse.of(
                    product.getId(),
                    List.of()
            );
        }

        List<Long> variantIds =
                variants.stream()
                        .map(ProductVariant::getId)
                        .toList();

        List<ProductVariantOptionValue> mappings =
                productVariantOptionValueRepository
                        .findAllByVariantIdIn(
                                variantIds
                        );

        Map<Long, List<ProductVariantOptionValue>>
                mappingsByVariantId =
                mappings.stream()
                        .collect(
                                Collectors.groupingBy(
                                        mapping ->
                                                mapping.getVariant()
                                                        .getId()
                                )
                        );

        List<ProductVariantResponse> responses =
                variants.stream()
                        .map(variant ->
                                ProductVariantResponse.from(
                                        variant,
                                        mappingsByVariantId
                                                .getOrDefault(
                                                        variant.getId(),
                                                        List.of()
                                                )
                                )
                        )
                        .toList();

        return ProductVariantListResponse.of(
                product.getId(),
                responses
        );
    }

    private String normalizeSkuCode(
            String skuCode
    ) {
        return skuCode
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private Seller getActiveSeller(
            Long userId
    ) {
        if (userId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }

        Seller seller =
                sellerRepository.findByUserId(userId)
                        .orElseThrow(() ->
                                new ProductException(
                                        "판매자 정보를 찾을 수 없습니다."
                                )
                        );

        if (seller.getStatus()
                != SellerStatus.ACTIVE) {
            throw new ProductException(
                    "활성 상태의 판매자만 상품을 관리할 수 있습니다."
            );
        }

        return seller;
    }

    private Product getSellerProduct(
            Long productId,
            Long sellerId
    ) {
        return productRepository
                .findByIdAndSellerId(
                        productId,
                        sellerId
                )
                .orElseThrow(() ->
                        new ProductException(
                                "상품을 찾을 수 없습니다."
                        )
                );
    }

    private void synchronizeProductStock(
            Product product,
            Long productId
    ) {
        int totalStockQuantity =
                productVariantRepository
                        .findAllByProductIdAndActiveTrueOrderByIdAsc(
                                productId
                        )
                        .stream()
                        .mapToInt(
                                ProductVariant::getStockQuantity
                        )
                        .sum();

        product.changeStockQuantity(
                totalStockQuantity
        );
    }
}