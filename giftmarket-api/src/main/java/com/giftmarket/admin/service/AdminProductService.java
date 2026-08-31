package com.giftmarket.admin.service;

import com.giftmarket.admin.dto.request.AdminProductDeletedFilter;
import com.giftmarket.admin.dto.response.AdminProductDetailResponse;
import com.giftmarket.admin.dto.response.AdminProductPageResponse;
import com.giftmarket.admin.dto.response.AdminProductSummaryResponse;
import com.giftmarket.admin.exception.AdminProductException;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.inquiry.repository.ProductInquiryRepository;
import com.giftmarket.product.entity.*;
import com.giftmarket.product.repository.*;
import com.giftmarket.review.repository.ReviewRepository;
import com.giftmarket.review.repository.ReviewSummaryProjection;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminProductService {

    private static final Sort PRODUCT_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id")
    );

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductOptionGroupRepository productOptionGroupRepository;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantOptionValueRepository productVariantOptionValueRepository;
    private final ReviewRepository reviewRepository;
    private final ProductInquiryRepository productInquiryRepository;

    @Transactional(readOnly = true)
    public AdminProductPageResponse getProducts(
            Long adminUserId, int page, int size, String keyword,
            ProductStatus status, Long sellerId, Long categoryId,
            AdminProductDeletedFilter deletedFilter
    ) {
        getAdmin(adminUserId);
        var productPage = productRepository.findAdminProducts(
                normalizeKeyword(keyword), status, sellerId, categoryId,
                deletedValue(deletedFilter), PageRequest.of(page, size, PRODUCT_SORT)
        );
        List<Product> products = productPage.getContent();
        Map<Long, Long> stocks = availableStocks(products);
        List<AdminProductSummaryResponse> content = products.stream()
                .map(product -> AdminProductSummaryResponse.from(
                        product, stocks.getOrDefault(product.getId(), 0L)
                )).toList();
        return AdminProductPageResponse.from(productPage, content);
    }

    @Transactional(readOnly = true)
    public AdminProductDetailResponse getProduct(Long adminUserId, Long productId) {
        getAdmin(adminUserId);
        Product product = productRepository.findAdminById(productId)
                .orElseThrow(() -> new AdminProductException("상품을 찾을 수 없습니다."));
        List<ProductImage> images = productImageRepository.findAllByProductIdOrderBySortOrderAsc(productId);
        List<ProductOptionGroup> groups = productOptionGroupRepository.findAllByProductIdOrderBySortOrderAsc(productId);
        List<Long> groupIds = groups.stream().map(ProductOptionGroup::getId).toList();
        List<ProductOptionValue> values = groupIds.isEmpty() ? List.of()
                : productOptionValueRepository.findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(groupIds);
        List<ProductVariant> variants = productVariantRepository.findAllByProductIdOrderByIdAsc(productId);
        List<Long> variantIds = variants.stream().map(ProductVariant::getId).toList();
        List<ProductVariantOptionValue> variantValues = variantIds.isEmpty() ? List.of()
                : productVariantOptionValueRepository.findAllByVariantIdIn(variantIds);
        ReviewSummaryProjection reviewSummary = reviewRepository.summarize(productId);

        return AdminProductDetailResponse.from(
                product, images, groups, values, variants, variantValues, reviewSummary,
                productInquiryRepository.countByProductIdAndDeletedAtIsNull(productId)
        );
    }

    private Map<Long, Long> availableStocks(List<Product> products) {
        if (products.isEmpty()) return Map.of();
        List<Long> productIds = products.stream().map(Product::getId).toList();
        Set<Long> productsWithOptions = new HashSet<>(
                productOptionGroupRepository.findProductIdsWithOptions(productIds)
        );
        Map<Long, Long> variantStocks = productsWithOptions.isEmpty() ? Map.of()
                : productVariantRepository.sumActiveStockByProductIds(productsWithOptions).stream()
                        .collect(Collectors.toMap(
                                ProductStockProjection::getProductId,
                                ProductStockProjection::getStockQuantity
                        ));
        return products.stream().collect(Collectors.toMap(
                Product::getId,
                product -> productsWithOptions.contains(product.getId())
                        ? variantStocks.getOrDefault(product.getId(), 0L)
                        : product.getStockQuantity().longValue()
        ));
    }

    private Boolean deletedValue(AdminProductDeletedFilter filter) {
        if (filter == null || filter == AdminProductDeletedFilter.ALL) return null;
        return filter == AdminProductDeletedFilter.DELETED;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private User getAdmin(Long adminUserId) {
        if (adminUserId == null) throw new AuthenticationException("인증이 필요합니다.");
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new AuthenticationException("사용자를 찾을 수 없습니다."));
        if (admin.getRole() != UserRole.ADMIN) throw new AuthenticationException("관리자 권한이 필요합니다.");
        return admin;
    }
}
