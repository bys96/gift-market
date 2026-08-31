package com.giftmarket.admin.service;

import com.giftmarket.admin.dto.request.AdminProductDeletedFilter;
import com.giftmarket.admin.exception.AdminProductException;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.inquiry.repository.ProductInquiryRepository;
import com.giftmarket.product.entity.*;
import com.giftmarket.product.repository.*;
import com.giftmarket.review.repository.ReviewRepository;
import com.giftmarket.review.repository.ReviewSummaryProjection;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    @Mock UserRepository userRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductImageRepository imageRepository;
    @Mock ProductOptionGroupRepository groupRepository;
    @Mock ProductOptionValueRepository valueRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock ProductVariantOptionValueRepository variantValueRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock ProductInquiryRepository inquiryRepository;
    @Mock User admin;

    private AdminProductService service;

    @BeforeEach
    void setUp() {
        service = new AdminProductService(
                userRepository, productRepository, imageRepository, groupRepository,
                valueRepository, variantRepository, variantValueRepository,
                reviewRepository, inquiryRepository
        );
    }

    @Test
    void rejectsNullAdminId() {
        assertThatThrownBy(() -> service.getProducts(null, 0, 20, null, null, null, null, AdminProductDeletedFilter.ALL))
                .isInstanceOf(AuthenticationException.class).hasMessage("인증이 필요합니다.");
        verify(productRepository, never()).findAdminProducts(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsMissingAndNonAdminUsers() {
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getProduct(ADMIN_ID, PRODUCT_ID))
                .isInstanceOf(AuthenticationException.class).hasMessage("사용자를 찾을 수 없습니다.");

        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
        given(admin.getRole()).willReturn(UserRole.USER);
        assertThatThrownBy(() -> service.getProduct(ADMIN_ID, PRODUCT_ID))
                .isInstanceOf(AuthenticationException.class).hasMessage("관리자 권한이 필요합니다.");
    }

    @Test
    void passesTrimmedFiltersPaginationSortAndAllDeletedPolicy() {
        givenAdmin();
        given(productRepository.findAdminProducts(any(), any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        service.getProducts(ADMIN_ID, 2, 20, "  선물  ", ProductStatus.ON_SALE, 3L, 4L, AdminProductDeletedFilter.ALL);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAdminProducts(
                eq("선물"), eq(ProductStatus.ON_SALE), eq(3L), eq(4L), isNull(), pageable.capture()
        );
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void mapsActiveAndDeletedFilters() {
        givenAdmin();
        given(productRepository.findAdminProducts(any(), any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        service.getProducts(ADMIN_ID, 0, 20, null, null, null, null, AdminProductDeletedFilter.ACTIVE);
        service.getProducts(ADMIN_ID, 0, 20, null, null, null, null, AdminProductDeletedFilter.DELETED);
        verify(productRepository).findAdminProducts(isNull(), isNull(), isNull(), isNull(), eq(false), any());
        verify(productRepository).findAdminProducts(isNull(), isNull(), isNull(), isNull(), eq(true), any());
    }

    @Test
    void calculatesPlainAndOptionProductStockInBatches() {
        givenAdmin();
        Product plain = summaryProduct(10L, 4);
        Product option = summaryProduct(11L, 99);
        ProductStockProjection stock = org.mockito.Mockito.mock(ProductStockProjection.class);
        given(productRepository.findAdminProducts(any(), any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(plain, option)));
        given(groupRepository.findProductIdsWithOptions(List.of(10L, 11L))).willReturn(List.of(11L));
        given(variantRepository.sumActiveStockByProductIds(anyCollection())).willReturn(List.of(stock));
        given(stock.getProductId()).willReturn(11L);
        given(stock.getStockQuantity()).willReturn(7L);

        var response = service.getProducts(ADMIN_ID, 0, 20, null, null, null, null, AdminProductDeletedFilter.ALL);

        assertThat(response.content().get(0).availableStock()).isEqualTo(4L);
        assertThat(response.content().get(1).availableStock()).isEqualTo(7L);
        assertThat(response.content().get(1).sellerId()).isEqualTo(30L);
    }

    @Test
    void throwsNotFoundForMissingProduct() {
        givenAdmin();
        given(productRepository.findAdminById(PRODUCT_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getProduct(ADMIN_ID, PRODUCT_ID))
                .isInstanceOf(AdminProductException.class).hasMessage("상품을 찾을 수 없습니다.");
    }

    @Test
    void returnsRegularProductWithoutOptions() {
        givenAdmin();
        Product product = detailProduct(false);
        ReviewSummaryProjection review = org.mockito.Mockito.mock(ReviewSummaryProjection.class);
        given(productRepository.findAdminById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(imageRepository.findAllByProductIdOrderBySortOrderAsc(PRODUCT_ID)).willReturn(List.of());
        given(groupRepository.findAllByProductIdOrderBySortOrderAsc(PRODUCT_ID)).willReturn(List.of());
        given(variantRepository.findAllByProductIdOrderByIdAsc(PRODUCT_ID)).willReturn(List.of());
        given(reviewRepository.summarize(PRODUCT_ID)).willReturn(review);

        var response = service.getProduct(ADMIN_ID, PRODUCT_ID);

        assertThat(response.deleted()).isFalse();
        assertThat(response.availableStock()).isEqualTo(20L);
        assertThat(response.optionGroups()).isEmpty();
        assertThat(response.variants()).isEmpty();
    }

    @Test
    void returnsDeletedProductWithImagesOptionsAllVariantsAndSummaries() {
        givenAdmin();
        Product product = detailProduct(true);
        ProductImage image = org.mockito.Mockito.mock(ProductImage.class);
        ProductOptionGroup group = org.mockito.Mockito.mock(ProductOptionGroup.class);
        ProductOptionValue value = org.mockito.Mockito.mock(ProductOptionValue.class);
        ProductVariant active = variant(50L, true, 3);
        ProductVariant inactive = variant(51L, false, 9);
        ProductVariantOptionValue link = org.mockito.Mockito.mock(ProductVariantOptionValue.class);
        ReviewSummaryProjection review = org.mockito.Mockito.mock(ReviewSummaryProjection.class);
        given(productRepository.findAdminById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(imageRepository.findAllByProductIdOrderBySortOrderAsc(PRODUCT_ID)).willReturn(List.of(image));
        given(image.getObjectKey()).willReturn("products/10/gallery.jpg");
        given(groupRepository.findAllByProductIdOrderBySortOrderAsc(PRODUCT_ID)).willReturn(List.of(group));
        given(group.getId()).willReturn(40L);
        given(group.getName()).willReturn("색상");
        given(valueRepository.findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(List.of(40L))).willReturn(List.of(value));
        given(value.getId()).willReturn(41L);
        given(value.getValue()).willReturn("빨강");
        given(value.getOptionGroup()).willReturn(group);
        given(variantRepository.findAllByProductIdOrderByIdAsc(PRODUCT_ID)).willReturn(List.of(active, inactive));
        given(variantValueRepository.findAllByVariantIdIn(List.of(50L, 51L))).willReturn(List.of(link));
        given(link.getVariant()).willReturn(active);
        given(link.getOptionValue()).willReturn(value);
        given(reviewRepository.summarize(PRODUCT_ID)).willReturn(review);
        given(review.getReviewCount()).willReturn(5L);
        given(review.getAverageRating()).willReturn(4.5);
        given(inquiryRepository.countByProductIdAndDeletedAtIsNull(PRODUCT_ID)).willReturn(2L);

        var response = service.getProduct(ADMIN_ID, PRODUCT_ID);

        assertThat(response.deleted()).isTrue();
        assertThat(response.galleryImageKeys()).containsExactly("products/10/gallery.jpg");
        assertThat(response.optionGroups()).singleElement().satisfies(result -> assertThat(result.values()).hasSize(1));
        assertThat(response.variants()).hasSize(2).extracting(v -> v.active()).containsExactly(true, false);
        assertThat(response.availableStock()).isEqualTo(3L);
        assertThat(response.operationSummary().reviewCount()).isEqualTo(5L);
        assertThat(response.operationSummary().inquiryCount()).isEqualTo(2L);
    }

    private void givenAdmin() {
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
        given(admin.getRole()).willReturn(UserRole.ADMIN);
    }

    private Product summaryProduct(Long id, int stock) {
        Product product = org.mockito.Mockito.mock(Product.class);
        Seller seller = org.mockito.Mockito.mock(Seller.class);
        given(product.getId()).willReturn(id);
        given(product.getName()).willReturn("상품 " + id);
        given(product.getPrice()).willReturn(10_000L);
        org.mockito.Mockito.lenient().when(product.getStockQuantity()).thenReturn(stock);
        given(product.getSeller()).willReturn(seller);
        given(seller.getId()).willReturn(30L);
        given(seller.getStoreName()).willReturn("선물 상점");
        return product;
    }

    private Product detailProduct(boolean deleted) {
        Product product = summaryProduct(PRODUCT_ID, 20);
        Seller seller = product.getSeller();
        User owner = org.mockito.Mockito.mock(User.class);
        Category category = org.mockito.Mockito.mock(Category.class);
        org.mockito.Mockito.lenient().when(product.isDeleted()).thenReturn(deleted);
        given(product.getStatus()).willReturn(ProductStatus.HIDDEN);
        given(product.getCategory()).willReturn(category);
        given(category.getId()).willReturn(60L);
        given(category.getName()).willReturn("선물");
        given(seller.getStatus()).willReturn(SellerStatus.ACTIVE);
        given(seller.getUser()).willReturn(owner);
        given(owner.getId()).willReturn(70L);
        return product;
    }

    private ProductVariant variant(Long id, boolean active, int stock) {
        ProductVariant variant = org.mockito.Mockito.mock(ProductVariant.class);
        given(variant.getId()).willReturn(id);
        given(variant.getAdditionalPrice()).willReturn(1_000L);
        given(variant.getStockQuantity()).willReturn(stock);
        given(variant.isActive()).willReturn(active);
        return variant;
    }
}
