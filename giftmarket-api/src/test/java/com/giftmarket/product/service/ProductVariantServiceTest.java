package com.giftmarket.product.service;

import com.giftmarket.product.dto.request.ProductVariantRequest;
import com.giftmarket.product.dto.request.ProductVariantUpdateRequest;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyCollection;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    @Mock ProductRepository productRepository;
    @Mock ProductOptionGroupRepository optionGroupRepository;
    @Mock ProductOptionValueRepository optionValueRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock ProductVariantOptionValueRepository mappingRepository;
    @Mock SellerRepository sellerRepository;
    @InjectMocks ProductVariantService productVariantService;

    @Test
    void allowsNegativeAdditionalPriceWhenFinalPriceIsPositive() {
        assertThatCode(() ->
                ProductVariantService.validateFinalVariantPrice(
                        10_000L,
                        -1_000L
                )
        ).doesNotThrowAnyException();
    }

    @Test
    void allowsFinalPriceOfOneWon() {
        assertThatCode(() ->
                ProductVariantService.validateFinalVariantPrice(
                        10_000L,
                        -9_999L
                )
        ).doesNotThrowAnyException();
    }

    @Test
    void rejectsFinalPriceOfZeroWon() {
        assertThatThrownBy(() ->
                ProductVariantService.validateFinalVariantPrice(
                        10_000L,
                        -10_000L
                )
        )
                .isInstanceOf(ProductException.class)
                .hasMessage("옵션 적용 후 최종 판매가격은 1원 이상이어야 합니다.");
    }

    @Test
    void rejectsOverflowedFinalPrice() {
        assertThatThrownBy(() ->
                ProductVariantService.validateFinalVariantPrice(
                        Long.MAX_VALUE,
                        1L
                )
        )
                .isInstanceOf(ProductException.class)
                .hasMessage("옵션 적용 후 최종 판매가격을 확인해주세요.");
    }

    @Test
    void reactivatesExistingVariantAndRecalculatesStockWithoutCreatingDuplicate() {
        Seller seller = mock(Seller.class);
        Product product = mock(Product.class);
        ProductOptionGroup group = mock(ProductOptionGroup.class);
        ProductOptionValue value = mock(ProductOptionValue.class);
        ProductVariant variant = mock(ProductVariant.class);
        ProductVariantOptionValue mapping = mock(ProductVariantOptionValue.class);

        when(seller.getId()).thenReturn(2L);
        when(seller.getStatus()).thenReturn(SellerStatus.ACTIVE);
        when(sellerRepository.findByUserId(1L)).thenReturn(Optional.of(seller));
        when(product.getId()).thenReturn(3L);
        when(product.getPrice()).thenReturn(10_000L);
        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(3L, 2L)).thenReturn(Optional.of(product));
        when(group.getId()).thenReturn(4L);
        when(group.getSortOrder()).thenReturn(0);
        when(optionGroupRepository.findAllByProductIdOrderBySortOrderAsc(3L)).thenReturn(List.of(group));
        when(value.getId()).thenReturn(5L);
        when(value.getOptionGroup()).thenReturn(group);
        when(optionValueRepository.findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(anyCollection()))
                .thenReturn(List.of(value));
        when(variant.getId()).thenReturn(6L);
        when(variant.getCombinationKey()).thenReturn("4:5");
        when(variant.getStockQuantity()).thenReturn(7);
        when(variantRepository.findAllByProductIdOrderByIdAsc(3L)).thenReturn(List.of(variant));
        when(mapping.getVariant()).thenReturn(variant);
        when(mapping.getOptionValue()).thenReturn(value);
        when(mappingRepository.findAllByVariantIdIn(List.of(6L))).thenReturn(List.of(mapping));
        when(variantRepository.findAllByProductIdAndActiveTrueOrderByIdAsc(3L)).thenReturn(List.of(variant));

        productVariantService.updateProductVariants(1L, 3L,
                new ProductVariantUpdateRequest(List.of(
                        new ProductVariantRequest(6L, "SKU-30", List.of(5L), 500L, 7, true)
                )));

        verify(variant).update("SKU-30", "4:5", 500L, 7);
        verify(variant).activate();
        verify(product).changeStockQuantity(7);
        verify(variantRepository, never()).save(org.mockito.ArgumentMatchers.any(ProductVariant.class));
    }

    @Test
    void optionlessProductDeactivatesAllVariantsAndUsesZeroVariantStock() {
        Seller seller = mock(Seller.class);
        Product product = mock(Product.class);
        ProductVariant first = mock(ProductVariant.class);
        ProductVariant second = mock(ProductVariant.class);

        when(seller.getId()).thenReturn(2L);
        when(seller.getStatus()).thenReturn(SellerStatus.ACTIVE);
        when(sellerRepository.findByUserId(1L)).thenReturn(Optional.of(seller));
        when(product.getId()).thenReturn(3L);
        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(3L, 2L)).thenReturn(Optional.of(product));
        when(optionGroupRepository.findAllByProductIdOrderBySortOrderAsc(3L)).thenReturn(List.of());
        when(variantRepository.findAllByProductIdOrderByIdAsc(3L)).thenReturn(List.of(first, second));

        productVariantService.updateProductVariants(1L, 3L, new ProductVariantUpdateRequest(List.of()));

        verify(first).deactivate();
        verify(second).deactivate();
        verify(product).changeStockQuantity(0);
        verify(variantRepository, never()).delete(first);
        verify(variantRepository, never()).delete(second);
    }
}
