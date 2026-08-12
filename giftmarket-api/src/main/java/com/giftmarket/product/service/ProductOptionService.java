package com.giftmarket.product.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.product.dto.request.ProductOptionGroupRequest;
import com.giftmarket.product.dto.request.ProductOptionUpdateRequest;
import com.giftmarket.product.dto.request.ProductOptionValueRequest;
import com.giftmarket.product.dto.response.ProductOptionGroupResponse;
import com.giftmarket.product.dto.response.ProductOptionResponse;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductOptionGroup;
import com.giftmarket.product.entity.ProductOptionValue;
import com.giftmarket.product.exception.ProductException;
import com.giftmarket.product.repository.ProductOptionGroupRepository;
import com.giftmarket.product.repository.ProductOptionValueRepository;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.product.repository.ProductVariantOptionValueRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
public class ProductOptionService {

    private final ProductRepository productRepository;
    private final ProductOptionGroupRepository productOptionGroupRepository;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final ProductVariantOptionValueRepository
            productVariantOptionValueRepository;
    private final SellerRepository sellerRepository;

    @Transactional(readOnly = true)
    public ProductOptionResponse getProductOptions(
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
    public ProductOptionResponse updateProductOptions(
            Long userId,
            Long productId,
            ProductOptionUpdateRequest request
    ) {
        Seller seller = getActiveSeller(userId);

        Product product = getSellerProduct(
                productId,
                seller.getId()
        );

        List<ProductOptionGroupRequest> groupRequests =
                request.normalizedOptionGroups();

        validateOptionRequests(groupRequests);

        List<ProductOptionGroup> existingGroups =
                productOptionGroupRepository
                        .findAllByProductIdOrderBySortOrderAsc(
                                productId
                        );

        Map<Long, ProductOptionGroup> existingGroupMap =
                existingGroups.stream()
                        .collect(
                                Collectors.toMap(
                                        ProductOptionGroup::getId,
                                        Function.identity()
                                )
                        );

        List<Long> existingGroupIds = existingGroups.stream()
                .map(ProductOptionGroup::getId)
                .toList();

        List<ProductOptionValue> existingValues =
                existingGroupIds.isEmpty()
                        ? List.of()
                        : productOptionValueRepository
                        .findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(
                                existingGroupIds
                        );

        Map<Long, List<ProductOptionValue>> valuesByGroupId =
                existingValues.stream()
                        .collect(
                                Collectors.groupingBy(
                                        value ->
                                                value.getOptionGroup().getId()
                                )
                        );

        Set<Long> requestedGroupIds = new HashSet<>();
        Set<Long> requestedValueIds = new HashSet<>();

        for (ProductOptionGroupRequest groupRequest : groupRequests) {
            ProductOptionGroup optionGroup;

            if (groupRequest.id() == null) {
                optionGroup = ProductOptionGroup.create(
                        product,
                        groupRequest.name().trim(),
                        groupRequest.sortOrder()
                );

                optionGroup =
                        productOptionGroupRepository.save(optionGroup);
            } else {
                optionGroup = existingGroupMap.get(
                        groupRequest.id()
                );

                if (optionGroup == null) {
                    throw new ProductException(
                            "해당 상품에 속하지 않은 옵션입니다."
                    );
                }

                if (!requestedGroupIds.add(optionGroup.getId())) {
                    throw new ProductException(
                            "동일한 옵션을 중복 요청할 수 없습니다."
                    );
                }

                optionGroup.update(
                        groupRequest.name().trim(),
                        groupRequest.sortOrder()
                );
            }

            updateOptionValues(
                    optionGroup,
                    groupRequest.normalizedValues(),
                    valuesByGroupId.getOrDefault(
                            optionGroup.getId(),
                            List.of()
                    ),
                    requestedValueIds
            );
        }

        deleteRemovedOptionGroups(
                existingGroups,
                requestedGroupIds
        );

        productOptionGroupRepository.flush();
        productOptionValueRepository.flush();

        return createResponse(product);
    }

    private void updateOptionValues(
            ProductOptionGroup optionGroup,
            List<ProductOptionValueRequest> valueRequests,
            List<ProductOptionValue> existingValues,
            Set<Long> requestedValueIds
    ) {
        Map<Long, ProductOptionValue> existingValueMap =
                existingValues.stream()
                        .collect(
                                Collectors.toMap(
                                        ProductOptionValue::getId,
                                        Function.identity()
                                )
                        );

        Set<Long> requestedValueIdsInGroup = new HashSet<>();

        for (ProductOptionValueRequest valueRequest : valueRequests) {
            if (valueRequest.id() == null) {
                ProductOptionValue optionValue =
                        ProductOptionValue.create(
                                optionGroup,
                                valueRequest.value().trim(),
                                valueRequest.sortOrder()
                        );

                productOptionValueRepository.save(optionValue);

                continue;
            }

            ProductOptionValue optionValue =
                    existingValueMap.get(valueRequest.id());

            if (optionValue == null) {
                throw new ProductException(
                        "해당 옵션에 속하지 않은 옵션 값입니다."
                );
            }

            if (!requestedValueIdsInGroup.add(optionValue.getId())
                    || !requestedValueIds.add(optionValue.getId())) {
                throw new ProductException(
                        "동일한 옵션 값을 중복 요청할 수 없습니다."
                );
            }

            optionValue.update(
                    valueRequest.value().trim(),
                    valueRequest.sortOrder()
            );
        }

        for (ProductOptionValue existingValue : existingValues) {
            if (requestedValueIdsInGroup.contains(
                    existingValue.getId()
            )) {
                continue;
            }

            validateOptionValueCanBeDeleted(existingValue);

            productOptionValueRepository.delete(existingValue);
        }
    }

    private void deleteRemovedOptionGroups(
            List<ProductOptionGroup> existingGroups,
            Set<Long> requestedGroupIds
    ) {
        for (ProductOptionGroup existingGroup : existingGroups) {
            if (requestedGroupIds.contains(existingGroup.getId())) {
                continue;
            }

            List<ProductOptionValue> optionValues =
                    productOptionValueRepository
                            .findAllByOptionGroupIdOrderBySortOrderAsc(
                                    existingGroup.getId()
                            );

            for (ProductOptionValue optionValue : optionValues) {
                validateOptionValueCanBeDeleted(optionValue);
            }

            productOptionValueRepository.deleteAll(optionValues);
            productOptionGroupRepository.delete(existingGroup);
        }
    }

    private void validateOptionValueCanBeDeleted(
            ProductOptionValue optionValue
    ) {
        if (productVariantOptionValueRepository.existsByOptionValueId(
                optionValue.getId()
        )) {
            throw new ProductException(
                    "상품 옵션 조합에서 사용 중인 옵션 값은 삭제할 수 없습니다."
            );
        }
    }

    private void validateOptionRequests(
            List<ProductOptionGroupRequest> groupRequests
    ) {
        Set<String> groupNames = new HashSet<>();
        Set<Integer> groupSortOrders = new HashSet<>();

        for (ProductOptionGroupRequest groupRequest : groupRequests) {
            String normalizedGroupName = normalizeForComparison(
                    groupRequest.name()
            );

            if (!groupNames.add(normalizedGroupName)) {
                throw new ProductException(
                        "동일한 옵션명을 중복 등록할 수 없습니다."
                );
            }

            if (!groupSortOrders.add(groupRequest.sortOrder())) {
                throw new ProductException(
                        "옵션 순서는 중복될 수 없습니다."
                );
            }

            validateOptionValueRequests(
                    groupRequest.normalizedValues()
            );
        }
    }

    private void validateOptionValueRequests(
            List<ProductOptionValueRequest> valueRequests
    ) {
        Set<String> values = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();

        for (ProductOptionValueRequest valueRequest : valueRequests) {
            String normalizedValue = normalizeForComparison(
                    valueRequest.value()
            );

            if (!values.add(normalizedValue)) {
                throw new ProductException(
                        "동일한 옵션 값을 중복 등록할 수 없습니다."
                );
            }

            if (!sortOrders.add(valueRequest.sortOrder())) {
                throw new ProductException(
                        "옵션 값 순서는 중복될 수 없습니다."
                );
            }
        }
    }

    private ProductOptionResponse createResponse(
            Product product
    ) {
        List<ProductOptionGroup> optionGroups =
                productOptionGroupRepository
                        .findAllByProductIdOrderBySortOrderAsc(
                                product.getId()
                        );

        if (optionGroups.isEmpty()) {
            return ProductOptionResponse.of(
                    product.getId(),
                    List.of()
            );
        }

        List<Long> optionGroupIds = optionGroups.stream()
                .map(ProductOptionGroup::getId)
                .toList();

        List<ProductOptionValue> optionValues =
                productOptionValueRepository
                        .findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(
                                optionGroupIds
                        );

        Map<Long, List<ProductOptionValue>> valuesByGroupId =
                new HashMap<>();

        for (ProductOptionValue optionValue : optionValues) {
            valuesByGroupId
                    .computeIfAbsent(
                            optionValue.getOptionGroup().getId(),
                            key -> new ArrayList<>()
                    )
                    .add(optionValue);
        }

        List<ProductOptionGroupResponse> responses =
                optionGroups.stream()
                        .map(optionGroup ->
                                ProductOptionGroupResponse.from(
                                        optionGroup,
                                        valuesByGroupId.getOrDefault(
                                                optionGroup.getId(),
                                                List.of()
                                        )
                                )
                        )
                        .toList();

        return ProductOptionResponse.of(
                product.getId(),
                responses
        );
    }

    private Seller getActiveSeller(Long userId) {
        if (userId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }

        Seller seller = sellerRepository.findByUserId(userId)
                .orElseThrow(() -> new ProductException(
                        "판매자 정보를 찾을 수 없습니다."
                ));

        if (seller.getStatus() != SellerStatus.ACTIVE) {
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
                .findByIdAndSellerIdAndDeletedAtIsNull(
                        productId,
                        sellerId
                )
                .orElseThrow(() -> new ProductException(
                        "상품을 찾을 수 없습니다."
                ));
    }

    private String normalizeForComparison(String value) {
        return value
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}