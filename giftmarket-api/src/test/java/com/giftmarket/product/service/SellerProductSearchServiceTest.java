package com.giftmarket.product.service;

import com.giftmarket.product.draft.repository.ProductDraftRepository;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.exception.ProductException;
import com.giftmarket.product.repository.*;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SellerProductSearchServiceTest {
    private ProductRepository products;
    private SellerRepository sellers;
    private Seller seller;
    private ProductService service;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        sellers = mock(SellerRepository.class);
        seller = mock(Seller.class);
        when(seller.getId()).thenReturn(12L);
        when(seller.getStatus()).thenReturn(SellerStatus.ACTIVE);
        when(sellers.findByUserId(7L)).thenReturn(Optional.of(seller));
        GiftOccasionService occasions = mock(GiftOccasionService.class);
        when(occasions.getActiveProductGiftOccasions(anyList())).thenReturn(Map.of());
        service = new ProductService(products, mock(ProductImageRepository.class), mock(ProductDraftRepository.class),
                mock(ProductOptionGroupRepository.class), mock(ProductOptionValueRepository.class),
                mock(ProductVariantRepository.class), mock(ProductVariantOptionValueRepository.class),
                mock(CategoryRepository.class), sellers, occasions, new ProductDescriptionSanitizer());
    }

    @Test
    void trimsKeywordAndUsesAuthenticatedSellerWithExistingPaginationAndSort() {
        when(products.findAllBySellerIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(eq(12L), eq("디퓨저"), any()))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(2)));
        var result = service.getMyProducts(7L, null, "  디퓨저  ", 2, 20);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.totalElements()).isZero();
        verify(products).findAllBySellerIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(eq(12L), eq("디퓨저"),
                argThat(page -> page.getPageNumber() == 2 && page.getPageSize() == 20
                        && page.getSort().getOrderFor("createdAt").getDirection() == Sort.Direction.DESC));
    }

    @Test
    void combinesStatusAndKeyword() {
        when(products.findAllBySellerIdAndStatusAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                eq(12L), eq(ProductStatus.ON_SALE), eq("디퓨저"), any())).thenReturn(Page.empty());
        service.getMyProducts(7L, ProductStatus.ON_SALE, "디퓨저", 0, 20);
        verify(products).findAllBySellerIdAndStatusAndNameContainingIgnoreCaseAndDeletedAtIsNull(
                eq(12L), eq(ProductStatus.ON_SALE), eq("디퓨저"), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankKeywordKeepsExistingUnfilteredQuery(String keyword) {
        when(products.findAllBySellerIdAndDeletedAtIsNull(eq(12L), any())).thenReturn(Page.empty());
        service.getMyProducts(7L, null, keyword, 0, 20);
        verify(products).findAllBySellerIdAndDeletedAtIsNull(eq(12L), any());
    }

    @Test
    void blankKeywordKeepsExistingStatusFilter() {
        when(products.findAllBySellerIdAndStatusAndDeletedAtIsNull(eq(12L), eq(ProductStatus.HIDDEN), any()))
                .thenReturn(Page.empty());
        service.getMyProducts(7L, ProductStatus.HIDDEN, " ", 0, 20);
        verify(products).findAllBySellerIdAndStatusAndDeletedAtIsNull(eq(12L), eq(ProductStatus.HIDDEN), any());
    }

    @Test
    void enforcesKeywordBoundaryBeforeDatabaseSearch() {
        String maximum = "가".repeat(100);
        when(products.findAllBySellerIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(eq(12L), eq(maximum), any()))
                .thenReturn(Page.empty());
        service.getMyProducts(7L, null, maximum, 0, 20);
        assertThatThrownBy(() -> service.getMyProducts(7L, null, maximum + "가", 0, 20))
                .isInstanceOf(ProductException.class).hasMessageContaining("100자");
        verify(products, times(1)).findAllBySellerIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(anyLong(), anyString(), any());
    }

    @Test
    void rejectsMissingOrInactiveSellerBeforeSearching() {
        when(sellers.findByUserId(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getMyProducts(8L, null, "디퓨저", 0, 20)).isInstanceOf(ProductException.class);
        when(seller.getStatus()).thenReturn(SellerStatus.SUSPENDED);
        assertThatThrownBy(() -> service.getMyProducts(7L, null, "디퓨저", 0, 20)).isInstanceOf(ProductException.class);
        verifyNoInteractions(products);
    }
}
