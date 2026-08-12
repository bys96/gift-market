package com.giftmarket.product.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.product.dto.request.ProductCreateRequest;
import com.giftmarket.product.dto.request.ProductSearchCondition;
import com.giftmarket.product.dto.request.ProductStatusUpdateRequest;
import com.giftmarket.product.dto.request.ProductStockUpdateRequest;
import com.giftmarket.product.dto.request.ProductUpdateRequest;
import com.giftmarket.product.dto.response.ProductDetailResponse;
import com.giftmarket.product.dto.response.ProductListResponse;
import com.giftmarket.product.dto.response.ProductPageResponse;
import com.giftmarket.product.dto.response.ProductResponse;
import com.giftmarket.product.dto.response.ProductSummaryResponse;
import com.giftmarket.product.dto.response.SellerProductPageResponse;
import com.giftmarket.product.entity.Category;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductImage;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.entity.ProductOptionGroup;
import com.giftmarket.product.entity.ProductOptionValue;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.entity.ProductVariantOptionValue;
import com.giftmarket.product.exception.ProductException;
import com.giftmarket.product.repository.CategoryRepository;
import com.giftmarket.product.repository.ProductImageRepository;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.product.repository.ProductOptionGroupRepository;
import com.giftmarket.product.repository.ProductOptionValueRepository;
import com.giftmarket.product.repository.ProductVariantOptionValueRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
import com.giftmarket.product.repository.ProductSpecifications;
import com.giftmarket.product.draft.repository.ProductDraftRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductDraftRepository productDraftRepository;

    private final ProductOptionGroupRepository
            productOptionGroupRepository;

    private final ProductOptionValueRepository
            productOptionValueRepository;

    private final ProductVariantRepository
            productVariantRepository;

    private final ProductVariantOptionValueRepository
            productVariantOptionValueRepository;

    private final CategoryRepository categoryRepository;
    private final SellerRepository sellerRepository;

    private final ProductDescriptionSanitizer
            productDescriptionSanitizer;

    @Transactional
    public ProductResponse createProduct(
            Long userId,
            ProductCreateRequest request
    ) {
        Seller seller = getActiveSeller(userId);
        Category category = getActiveCategory(request.categoryId());

        String representativeImageKey = normalizeImageKey(
                request.representativeImageKey()
        );

        List<String> galleryImageKeys = normalizeGalleryImageKeys(
                request.normalizedGalleryImageKeys()
        );

        String description = sanitizeDescription(
                request.description()
        );

        validateImageKeys(
                seller,
                representativeImageKey,
                galleryImageKeys
        );

        if (Boolean.TRUE.equals(request.startSale())) {
            validateSaleRequirements(
                    representativeImageKey,
                    description
            );
        }

        Product product = Product.createDraft(
                seller,
                category,
                request.name().trim(),
                trimToNull(request.brandName()),
                trimToNull(request.summary()),
                description,
                request.price(),
                request.stockQuantity(),
                representativeImageKey,
                request.freeShipping(),
                request.normalizedShippingFee(),
                request.shippingPreparationDays(),
                request.returnShippingFee(),
                request.exchangeShippingFee()
        );

        Product savedProduct = productRepository.save(product);

        List<ProductImage> productImages = saveProductImages(
                savedProduct,
                galleryImageKeys
        );

        if (Boolean.TRUE.equals(request.startSale())) {
            savedProduct.startSale();
        }

        return ProductResponse.from(
                savedProduct,
                productImages
        );
    }

    @Transactional(readOnly = true)
    public ProductPageResponse getProducts(
            Long categoryId,
            String keyword,
            boolean excludeSoldOut,
            int page,
            int size
    ) {
        List<Long> categoryIds = categoryId == null
                ? List.of()
                : List.of(categoryId);

        ProductSearchCondition condition =
                new ProductSearchCondition(
                        categoryIds,
                        keyword,
                        excludeSoldOut,
                        page,
                        size
                );

        return getProducts(condition);
    }

    @Transactional(readOnly = true)
    public ProductPageResponse getProducts(
            ProductSearchCondition condition
    ) {
        Pageable pageable = createPageable(
                condition.normalizedPage(),
                condition.normalizedSize()
        );

        List<ProductStatus> statuses =
                condition.normalizedExcludeSoldOut()
                        ? List.of(ProductStatus.ON_SALE)
                        : List.of(
                        ProductStatus.ON_SALE,
                        ProductStatus.SOLD_OUT
                );

        Specification<Product> specification =
                Specification
                        .where(
                                ProductSpecifications.notDeleted()
                        )
                        .and(
                                ProductSpecifications.statusIn(
                                        statuses
                                )
                        )
                        .and(
                                ProductSpecifications.categoryIdIn(
                                        condition.normalizedCategoryIds()
                                )
                        )
                        .and(
                                ProductSpecifications.nameContains(
                                        condition.normalizedKeyword()
                                )
                        );

        Page<Product> productPage = productRepository.findAll(
                specification,
                pageable
        );

        Page<ProductSummaryResponse> responsePage =
                productPage.map(
                        ProductSummaryResponse::from
                );

        return ProductPageResponse.from(responsePage);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(Long productId) {
        Product product = productRepository
                .findByIdAndStatusInAndDeletedAtIsNull(
                        productId,
                        List.of(
                                ProductStatus.ON_SALE,
                                ProductStatus.SOLD_OUT
                        )
                )
                .orElseThrow(() -> new ProductException(
                        "상품을 찾을 수 없습니다."
                ));

        List<ProductImage> productImages =
                productImageRepository
                        .findAllByProductIdOrderBySortOrderAsc(
                                product.getId()
                        );

        List<ProductOptionGroup> optionGroups =
                productOptionGroupRepository
                        .findAllByProductIdOrderBySortOrderAsc(
                                product.getId()
                        );

        List<ProductOptionValue> optionValues;

        if (optionGroups.isEmpty()) {
            optionValues = List.of();
        } else {
            List<Long> optionGroupIds =
                    optionGroups.stream()
                            .map(ProductOptionGroup::getId)
                            .toList();

            optionValues =
                    productOptionValueRepository
                            .findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(
                                    optionGroupIds
                            );
        }

        List<ProductVariant> variants =
                productVariantRepository
                        .findAllByProductIdAndActiveTrueOrderByIdAsc(
                                product.getId()
                        );

        List<ProductVariantOptionValue>
                variantOptionValues;

        if (variants.isEmpty()) {
            variantOptionValues = List.of();
        } else {
            List<Long> variantIds =
                    variants.stream()
                            .map(ProductVariant::getId)
                            .toList();

            variantOptionValues =
                    productVariantOptionValueRepository
                            .findAllByVariantIdIn(
                                    variantIds
                            );
        }

        return ProductDetailResponse.from(
                product,
                productImages,
                optionGroups,
                optionValues,
                variants,
                variantOptionValues
        );
    }

    @Transactional(readOnly = true)
    public SellerProductPageResponse getMyProducts(
            Long userId,
            ProductStatus status,
            int page,
            int size
    ) {
        Seller seller = getActiveSeller(userId);
        Pageable pageable = createPageable(page, size);

        Page<Product> productPage;

        if (status == null) {
            productPage =
                    productRepository
                            .findAllBySellerIdAndDeletedAtIsNull(
                                    seller.getId(),
                                    pageable
                            );
        } else {
            productPage =
                    productRepository
                            .findAllBySellerIdAndStatusAndDeletedAtIsNull(
                                    seller.getId(),
                                    status,
                                    pageable
                            );
        }

        Page<ProductListResponse> responsePage =
                productPage.map(
                        ProductListResponse::from
                );

        return SellerProductPageResponse.from(responsePage);
    }

    @Transactional(readOnly = true)
    public ProductResponse getMyProduct(
            Long userId,
            Long productId
    ) {
        Seller seller = getActiveSeller(userId);

        Product product = getSellerProduct(
                productId,
                seller.getId()
        );

        List<ProductImage> productImages = productImageRepository
                .findAllByProductIdOrderBySortOrderAsc(
                        product.getId()
                );

        return ProductResponse.from(
                product,
                productImages
        );
    }

    @Transactional
    public ProductResponse updateProduct(
            Long userId,
            Long productId,
            ProductUpdateRequest request
    ) {
        Seller seller = getActiveSeller(userId);

        Product product = getSellerProduct(
                productId,
                seller.getId()
        );

        Category category = getActiveCategory(
                request.categoryId()
        );

        String representativeImageKey = normalizeImageKey(
                request.representativeImageKey()
        );

        List<String> galleryImageKeys = normalizeGalleryImageKeys(
                request.normalizedGalleryImageKeys()
        );

        String description = sanitizeDescription(
                request.description()
        );

        validateImageKeys(
                seller,
                representativeImageKey,
                galleryImageKeys
        );

        if (product.getStatus() == ProductStatus.ON_SALE
                || product.getStatus() == ProductStatus.SOLD_OUT) {
            validateSaleRequirements(
                    representativeImageKey,
                    description
            );
        }

        product.update(
                category,
                request.name().trim(),
                trimToNull(request.brandName()),
                trimToNull(request.summary()),
                description,
                request.price(),
                request.stockQuantity(),
                representativeImageKey,
                request.freeShipping(),
                request.normalizedShippingFee(),
                request.shippingPreparationDays(),
                request.returnShippingFee(),
                request.exchangeShippingFee()
        );

        List<ProductImage> productImages = replaceProductImages(
                product,
                galleryImageKeys
        );

        return ProductResponse.from(
                product,
                productImages
        );
    }

    @Transactional
    public ProductResponse updateProductStatus(
            Long userId,
            Long productId,
            ProductStatusUpdateRequest request
    ) {
        Seller seller = getActiveSeller(userId);

        Product product = getSellerProduct(
                productId,
                seller.getId()
        );

        ProductStatus requestedStatus = request.status();

        if (requestedStatus == ProductStatus.SOLD_OUT) {
            throw new ProductException(
                    "품절 상태는 재고 수량에 따라 자동으로 변경됩니다."
            );
        }

        if (requestedStatus == ProductStatus.ON_SALE) {
            validateSaleRequirements(
                    product.getRepresentativeImageKey(),
                    product.getDescription()
            );
        }

        product.changeStatus(requestedStatus);

        List<ProductImage> productImages = productImageRepository
                .findAllByProductIdOrderBySortOrderAsc(
                        product.getId()
                );

        return ProductResponse.from(
                product,
                productImages
        );
    }

    @Transactional
    public void deleteProduct(
            Long userId,
            Long productId
    ) {
        Seller seller = getActiveSeller(userId);

        Product product = getSellerProduct(
                productId,
                seller.getId()
        );

        /*
         * 기존 상품 수정 중 생성된 Draft는
         * 삭제된 Product를 계속 참조할 이유가 없으므로 함께 제거합니다.
         *
         * 신규 상품 Draft(product_id = null)는 영향을 받지 않습니다.
         */
        productDraftRepository
                .deleteBySellerIdAndProductId(
                        seller.getId(),
                        product.getId()
                );

        product.softDelete();
    }

    @Transactional
    public ProductResponse updateProductStock(
            Long userId,
            Long productId,
            ProductStockUpdateRequest request
    ) {
        Seller seller = getActiveSeller(userId);

        Product product = getSellerProduct(
                productId,
                seller.getId()
        );

        product.changeStockQuantity(
                request.stockQuantity()
        );

        List<ProductImage> productImages = productImageRepository
                .findAllByProductIdOrderBySortOrderAsc(
                        product.getId()
                );

        return ProductResponse.from(
                product,
                productImages
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

    private Category getActiveCategory(Long categoryId) {
        return categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new ProductException(
                        "사용할 수 없는 카테고리입니다."
                ));
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

    private List<ProductImage> saveProductImages(
            Product product,
            List<String> galleryImageKeys
    ) {
        List<ProductImage> productImages = createProductImages(
                product,
                galleryImageKeys
        );

        if (productImages.isEmpty()) {
            return List.of();
        }

        return productImageRepository.saveAll(productImages);
    }

    private List<ProductImage> replaceProductImages(
            Product product,
            List<String> galleryImageKeys
    ) {
        productImageRepository.deleteAllByProductId(
                product.getId()
        );

        productImageRepository.flush();

        return saveProductImages(
                product,
                galleryImageKeys
        );
    }

    private List<ProductImage> createProductImages(
            Product product,
            List<String> galleryImageKeys
    ) {
        return IntStream.range(
                        0,
                        galleryImageKeys.size()
                )
                .mapToObj(index -> ProductImage.create(
                        product,
                        galleryImageKeys.get(index),
                        index
                ))
                .toList();
    }

    private void validateSaleRequirements(
            String representativeImageKey,
            String description
    ) {
        if (representativeImageKey == null) {
            throw new ProductException(
                    "판매를 시작하려면 대표 이미지를 등록해주세요."
            );
        }

        if (description == null || description.isBlank()) {
            throw new ProductException(
                    "판매를 시작하려면 상품 상세 설명을 입력해주세요."
            );
        }
    }

    private void validateImageKeys(
            Seller seller,
            String representativeImageKey,
            List<String> galleryImageKeys
    ) {
        if (representativeImageKey != null) {
            validateProductObjectKey(
                    seller,
                    representativeImageKey,
                    true
            );
        }

        Set<String> uniqueImageKeys = new HashSet<>();

        for (String galleryImageKey : galleryImageKeys) {
            if (galleryImageKey == null) {
                throw new ProductException(
                        "갤러리 이미지 키는 비어 있을 수 없습니다."
                );
            }

            validateProductObjectKey(
                    seller,
                    galleryImageKey,
                    false
            );

            if (!uniqueImageKeys.add(galleryImageKey)) {
                throw new ProductException(
                        "동일한 갤러리 이미지를 중복 등록할 수 없습니다."
                );
            }
        }

        if (representativeImageKey != null
                && uniqueImageKeys.contains(representativeImageKey)) {
            throw new ProductException(
                    "대표 이미지와 갤러리 이미지는 중복될 수 없습니다."
            );
        }
    }

    private void validateProductObjectKey(
            Seller seller,
            String objectKey,
            boolean representative
    ) {
        String expectedPrefix;

        if (representative) {
            expectedPrefix = "products/"
                    + seller.getId()
                    + "/representative/";
        } else {
            expectedPrefix = "products/"
                    + seller.getId()
                    + "/gallery/";
        }

        if (!objectKey.startsWith(expectedPrefix)
                || objectKey.length() <= expectedPrefix.length()
                || objectKey.contains("..")
                || objectKey.contains("\\")
                || objectKey.endsWith("/")) {
            throw new ProductException(
                    "유효하지 않은 상품 이미지입니다."
            );
        }
    }

    private List<String> normalizeGalleryImageKeys(
            List<String> galleryImageKeys
    ) {
        return galleryImageKeys.stream()
                .map(this::normalizeImageKey)
                .toList();
    }

    private String normalizeImageKey(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return null;
        }

        return imageKey.trim();
    }

    private String sanitizeDescription(String description) {
        String normalizedDescription = trimToNull(description);

        if (normalizedDescription == null) {
            return null;
        }

        String sanitizedDescription =
                productDescriptionSanitizer.sanitize(
                        normalizedDescription
                );

        return sanitizedDescription.isBlank()
                ? null
                : sanitizedDescription;
    }

    private Pageable createPageable(
            int page,
            int size
    ) {
        if (page < 0) {
            throw new ProductException(
                    "페이지 번호는 0 이상이어야 합니다."
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ProductException(
                    "페이지 크기는 1 이상 100 이하이어야 합니다."
            );
        }

        return PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Direction.DESC,
                        "createdAt"
                )
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}