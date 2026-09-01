package com.giftmarket.admin.dto.response;

import com.giftmarket.product.entity.*;
import com.giftmarket.review.repository.ReviewSummaryProjection;
import com.giftmarket.seller.entity.SellerStatus;

import java.time.LocalDateTime;
import java.util.*;

public record AdminProductDetailResponse(
        Long productId,
        String name,
        String brandName,
        String summary,
        String description,
        long price,
        long availableStock,
        ProductStatus status,
        boolean adminHidden,
        String adminHiddenReason,
        LocalDateTime adminHiddenAt,
        boolean deleted,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean freeShipping,
        long shippingFee,
        int shippingPreparationDays,
        long returnShippingFee,
        long exchangeShippingFee,
        SellerInfo seller,
        CategoryInfo category,
        String representativeImageKey,
        List<String> galleryImageKeys,
        List<OptionGroup> optionGroups,
        List<Variant> variants,
        OperationSummary operationSummary
) {
    public static AdminProductDetailResponse from(
            Product product,
            List<ProductImage> images,
            List<ProductOptionGroup> groups,
            List<ProductOptionValue> values,
            List<ProductVariant> variants,
            List<ProductVariantOptionValue> variantValues,
            ReviewSummaryProjection reviewSummary,
            long inquiryCount
    ) {
        Map<Long, List<ProductOptionValue>> valuesByGroup = new HashMap<>();
        values.forEach(value -> valuesByGroup.computeIfAbsent(
                value.getOptionGroup().getId(), ignored -> new ArrayList<>()
        ).add(value));
        Map<Long, List<ProductVariantOptionValue>> valuesByVariant = new HashMap<>();
        variantValues.forEach(value -> valuesByVariant.computeIfAbsent(
                value.getVariant().getId(), ignored -> new ArrayList<>()
        ).add(value));

        List<OptionGroup> groupResponses = groups.stream().map(group -> new OptionGroup(
                group.getId(), group.getName(), group.getSortOrder(),
                valuesByGroup.getOrDefault(group.getId(), List.of()).stream()
                        .map(value -> new OptionValue(value.getId(), value.getValue(), value.getSortOrder()))
                        .toList()
        )).toList();
        List<Variant> variantResponses = variants.stream().map(variant -> new Variant(
                variant.getId(), variant.getSkuCode(), variant.getCombinationKey(),
                variant.getAdditionalPrice(), product.getPrice() + variant.getAdditionalPrice(),
                variant.getStockQuantity(), variant.isActive(),
                valuesByVariant.getOrDefault(variant.getId(), List.of()).stream()
                        .map(link -> new VariantOptionValue(
                                link.getOptionValue().getId(),
                                link.getOptionValue().getOptionGroup().getName(),
                                link.getOptionValue().getValue()
                        )).toList()
        )).toList();
        long availableStock = groups.isEmpty()
                ? product.getStockQuantity()
                : variants.stream().filter(ProductVariant::isActive)
                        .mapToLong(ProductVariant::getStockQuantity).sum();
        Category parent = product.getCategory().getParent();

        return new AdminProductDetailResponse(
                product.getId(), product.getName(), product.getBrandName(), product.getSummary(),
                product.getDescription(), product.getPrice(), availableStock, product.getStatus(),
                product.isAdminHidden(), product.getAdminHiddenReason(), product.getAdminHiddenAt(),
                product.isDeleted(), product.getDeletedAt(), product.getCreatedAt(), product.getUpdatedAt(),
                product.isFreeShipping(), product.getShippingFee(), product.getShippingPreparationDays(),
                product.getReturnShippingFee(), product.getExchangeShippingFee(),
                new SellerInfo(
                        product.getSeller().getId(), product.getSeller().getStoreName(),
                        product.getSeller().getStatus(), product.getSeller().getUser().getId()
                ),
                new CategoryInfo(
                        product.getCategory().getId(), product.getCategory().getName(),
                        parent == null ? null : parent.getId(), parent == null ? null : parent.getName()
                ),
                product.getRepresentativeImageKey(),
                images.stream().map(ProductImage::getObjectKey).toList(),
                groupResponses, variantResponses,
                new OperationSummary(
                        reviewSummary.getReviewCount(), reviewSummary.getAverageRating(), inquiryCount
                )
        );
    }

    public record SellerInfo(Long sellerId, String storeName, SellerStatus status, Long ownerUserId) {}
    public record CategoryInfo(Long categoryId, String categoryName, Long parentCategoryId, String parentCategoryName) {}
    public record OptionGroup(Long optionGroupId, String name, int sortOrder, List<OptionValue> values) {}
    public record OptionValue(Long optionValueId, String value, int sortOrder) {}
    public record Variant(
            Long variantId, String skuCode, String combinationKey, long additionalPrice,
            long price, int stockQuantity, boolean active, List<VariantOptionValue> optionValues
    ) {}
    public record VariantOptionValue(Long optionValueId, String groupName, String value) {}
    public record OperationSummary(long reviewCount, double averageRating, long inquiryCount) {}
}
