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
    private final ProductVariantRepository productVariantRepository;
    private final ProductInventoryPolicy inventoryPolicy;

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

        ProductOptionResponse active = createResponse(product);
        List<ProductOptionGroup> allGroups = productOptionGroupRepository.findIncludingArchivedByProductId(productId);
        List<ProductOptionGroupResponse> archived = allGroups.stream().map(group -> {
            List<ProductOptionValue> values = productOptionValueRepository
                    .findIncludingArchivedByOptionGroupIdIn(List.of(group.getId())).stream()
                    .filter(value -> group.isArchived() || value.isArchived()).toList();
            return ProductOptionGroupResponse.from(group, values);
        }).filter(group -> group.isArchived() || !group.getValues().isEmpty()).toList();
        return ProductOptionResponse.builder().productId(productId).optionGroups(active.getOptionGroups())
                .archivedOptionGroups(archived).build();
    }

    @Transactional
    public void retireVariantsUsingRemovedOptions(
            Long userId,
            Long productId,
            ProductOptionUpdateRequest request
    ) {
        // Use the same Product-first lock order as orders, before snapshot reads.
        productRepository.findByIdForUpdate(productId);
        Seller seller = getActiveSeller(userId);
        getSellerProduct(productId, seller.getId());
        inventoryPolicy.requireEditable(productId);

        Set<Long> requestedValueIds = request.normalizedOptionGroups().stream()
                .flatMap(group -> group.normalizedValues().stream())
                .map(ProductOptionValueRequest::id)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        List<Long> groupIds = productOptionGroupRepository
                .findAllByProductIdOrderBySortOrderAsc(productId).stream()
                .map(ProductOptionGroup::getId)
                .toList();

        if (groupIds.isEmpty()) {
            return;
        }

        List<Long> removedValueIds = productOptionValueRepository
                .findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(groupIds).stream()
                .map(ProductOptionValue::getId)
                .filter(id -> !requestedValueIds.contains(id))
                .toList();

        if (removedValueIds.isEmpty()) {
            return;
        }

        List<ProductVariantOptionValue> removedMappings =
                productVariantOptionValueRepository.findAllByOptionValueIdIn(removedValueIds);

        removedMappings.stream()
                .map(ProductVariantOptionValue::getVariant)
                .filter(variant -> variant.getProduct().getId().equals(productId))
                .distinct()
                .forEach(variant -> variant.deactivate());

        // Preserve mappings for historical references and explicit restoration.
    }

    @Transactional
    public ProductOptionResponse updateProductOptions(Long userId, Long productId, ProductOptionUpdateRequest request) {
        return saveOptions(userId, productId, request, false);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ProductOptionResponse initializeProductOptions(Long userId, Long productId, ProductOptionUpdateRequest request) {
        return saveOptions(userId, productId, request, true);
    }

    private ProductOptionResponse saveOptions(Long userId, Long productId, ProductOptionUpdateRequest request,
            boolean initialRegistration) {
        // A current locking read must precede seller/product snapshot reads.
        productRepository.findByIdForUpdate(productId);
        Seller seller = getActiveSeller(userId);

        Product product = getSellerProduct(
                productId,
                seller.getId()
        );

        inventoryPolicy.requireConfiguration(product, !request.normalizedOptionGroups().isEmpty(), initialRegistration);
        retireVariantsUsingRemovedOptions(userId, productId, request);

        List<ProductOptionGroupRequest> groupRequests =
                request.normalizedOptionGroups();

        validateOptionRequests(groupRequests);

        List<ProductOptionGroup> existingGroups =
                productOptionGroupRepository.findIncludingArchivedByProductId(productId);

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
                        .findIncludingArchivedByOptionGroupIdIn(existingGroupIds);

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
                if (existingGroups.stream().anyMatch(group -> group.isArchived()
                        && normalizeForComparison(group.getName()).equals(normalizeForComparison(groupRequest.name())))) {
                    throw new ProductException("보관된 동일 옵션이 있습니다. 보관 옵션 복원에서 기존 옵션을 복원해주세요.");
                }
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

                if (!optionGroup.getName().equals(groupRequest.name().trim())
                        && valuesByGroupId.getOrDefault(optionGroup.getId(), List.of()).stream()
                        .anyMatch(value -> productVariantOptionValueRepository.existsByOptionValueId(value.getId()))) {
                    throw new ProductException("SKU에 연결된 옵션 이름은 변경할 수 없습니다. 새 옵션을 추가해주세요.");
                }
                optionGroup.restore();
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

        if (inventoryPolicy.usesVariants(productId)) {
            var activeGroupIds = productOptionGroupRepository.findAllByProductIdOrderBySortOrderAsc(productId)
                    .stream().map(ProductOptionGroup::getId).collect(Collectors.toSet());
            var allVariants = productVariantRepository.findAllByProductIdOrderByIdAsc(productId);
            var byVariant = productVariantOptionValueRepository
                    .findAllByVariantIdIn(allVariants.stream().map(v -> v.getId()).toList()).stream()
                    .collect(Collectors.groupingBy(mapping -> mapping.getVariant().getId()));
            for (var variant : allVariants) {
                var selected = byVariant.getOrDefault(variant.getId(), List.of());
                if (selected.stream().anyMatch(mapping -> mapping.getOptionValue().isArchived()
                        || mapping.getOptionValue().getOptionGroup().isArchived())
                        || !activeGroupIds.equals(selected.stream()
                        .map(mapping -> mapping.getOptionValue().getOptionGroup().getId()).collect(Collectors.toSet()))) {
                    variant.deactivate();
                }
            }
            productVariantRepository.flush();
            product.changeStockQuantity(productVariantRepository
                    .findAllByProductIdAndActiveTrueOrderByIdAsc(productId).stream()
                    .mapToInt(com.giftmarket.product.entity.ProductVariant::getStockQuantity).sum());
        }

        ProductOptionResponse active = createResponse(product);
        List<ProductOptionGroup> allGroups = productOptionGroupRepository.findIncludingArchivedByProductId(productId);
        List<ProductOptionGroupResponse> archived = allGroups.stream().map(group -> {
            List<ProductOptionValue> values = productOptionValueRepository
                    .findIncludingArchivedByOptionGroupIdIn(List.of(group.getId())).stream()
                    .filter(value -> group.isArchived() || value.isArchived()).toList();
            return ProductOptionGroupResponse.from(group, values);
        }).filter(group -> group.isArchived() || !group.getValues().isEmpty()).toList();
        return ProductOptionResponse.builder().productId(productId).optionGroups(active.getOptionGroups())
                .archivedOptionGroups(archived).build();
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
                if (existingValues.stream().anyMatch(value -> value.isArchived()
                        && normalizeForComparison(value.getValue()).equals(normalizeForComparison(valueRequest.value())))) {
                    throw new ProductException("보관된 동일 옵션 값이 있습니다. 보관 옵션 복원에서 기존 옵션을 복원해주세요.");
                }
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

            if (!optionValue.getValue().equals(valueRequest.value().trim())
                    && productVariantOptionValueRepository.existsByOptionValueId(optionValue.getId())) {
                throw new ProductException("SKU에 연결된 옵션 값은 이름을 변경할 수 없습니다. 실물 변경은 새 옵션 값으로 등록해주세요.");
            }
            optionValue.restore();
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

            existingValue.archive();
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

            optionValues.forEach(ProductOptionValue::archive);
            existingGroup.archive();
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
