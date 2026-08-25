package com.giftmarket.product.service;

import com.giftmarket.product.dto.request.ProductOptionGroupRequest;
import com.giftmarket.product.dto.request.ProductOptionUpdateRequest;
import com.giftmarket.product.dto.request.ProductOptionValueRequest;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductOptionGroup;
import com.giftmarket.product.entity.ProductOptionValue;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.entity.ProductVariantOptionValue;
import com.giftmarket.product.repository.ProductOptionGroupRepository;
import com.giftmarket.product.repository.ProductOptionValueRepository;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.product.repository.ProductVariantOptionValueRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.order.entity.OrderItem;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProductOptionServiceTest {

    @Mock ProductRepository productRepository;
    @Mock ProductOptionGroupRepository optionGroupRepository;
    @Mock ProductOptionValueRepository optionValueRepository;
    @Mock ProductVariantOptionValueRepository mappingRepository;
    @Mock SellerRepository sellerRepository;
    @InjectMocks ProductOptionService productOptionService;

    @Test
    void removedOptionValueDeactivatesVariantWithoutDeletingVariant() {
        Seller seller = mock(Seller.class);
        Product product = mock(Product.class);
        ProductOptionGroup group = mock(ProductOptionGroup.class);
        ProductOptionValue keptValue = mock(ProductOptionValue.class);
        ProductOptionValue removedValue = mock(ProductOptionValue.class);
        ProductVariant variant = mock(ProductVariant.class);
        ProductVariant keptVariant = mock(ProductVariant.class);
        ProductVariantOptionValue mapping = mock(ProductVariantOptionValue.class);

        when(seller.getId()).thenReturn(2L);
        when(seller.getStatus()).thenReturn(SellerStatus.ACTIVE);
        when(sellerRepository.findByUserId(1L)).thenReturn(Optional.of(seller));
        when(product.getId()).thenReturn(3L);
        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(3L, 2L))
                .thenReturn(Optional.of(product));
        when(group.getId()).thenReturn(4L);
        when(optionGroupRepository.findAllByProductIdOrderBySortOrderAsc(3L))
                .thenReturn(List.of(group));
        when(keptValue.getId()).thenReturn(5L);
        when(removedValue.getId()).thenReturn(6L);
        when(optionValueRepository.findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(List.of(4L)))
                .thenReturn(List.of(keptValue, removedValue));
        when(mappingRepository.findAllByOptionValueIdIn(List.of(6L)))
                .thenReturn(List.of(mapping));
        when(mapping.getVariant()).thenReturn(variant);
        when(variant.getProduct()).thenReturn(product);

        ProductOptionUpdateRequest request = new ProductOptionUpdateRequest(List.of(
                new ProductOptionGroupRequest(4L, "용량", 0,
                        List.of(new ProductOptionValueRequest(5L, "30ml", 0)))
        ));

        productOptionService.retireVariantsUsingRemovedOptions(1L, 3L, request);

        verify(variant).deactivate();
        verify(keptVariant, never()).deactivate();
        verify(mappingRepository).deleteAll(List.of(mapping));
        verify(productRepository, never()).delete(product);
    }

    @Test
    void removingWholeOptionGroupRetiresEveryVariantUsingItsValues() {
        Seller seller = mock(Seller.class);
        Product product = mock(Product.class);
        ProductOptionGroup capacity = mock(ProductOptionGroup.class);
        ProductOptionGroup scent = mock(ProductOptionGroup.class);
        ProductOptionValue capacityValue = mock(ProductOptionValue.class);
        ProductOptionValue woody = mock(ProductOptionValue.class);
        ProductOptionValue floral = mock(ProductOptionValue.class);
        ProductVariant woodyVariant = mock(ProductVariant.class);
        ProductVariant floralVariant = mock(ProductVariant.class);
        ProductVariantOptionValue woodyMapping = mock(ProductVariantOptionValue.class);
        ProductVariantOptionValue floralMapping = mock(ProductVariantOptionValue.class);

        when(seller.getId()).thenReturn(2L);
        when(seller.getStatus()).thenReturn(SellerStatus.ACTIVE);
        when(sellerRepository.findByUserId(1L)).thenReturn(Optional.of(seller));
        when(product.getId()).thenReturn(3L);
        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(3L, 2L)).thenReturn(Optional.of(product));
        when(capacity.getId()).thenReturn(4L);
        when(scent.getId()).thenReturn(7L);
        when(optionGroupRepository.findAllByProductIdOrderBySortOrderAsc(3L)).thenReturn(List.of(capacity, scent));
        when(capacityValue.getId()).thenReturn(5L);
        when(woody.getId()).thenReturn(8L);
        when(floral.getId()).thenReturn(9L);
        when(optionValueRepository.findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(List.of(4L, 7L)))
                .thenReturn(List.of(capacityValue, woody, floral));
        when(mappingRepository.findAllByOptionValueIdIn(List.of(8L, 9L)))
                .thenReturn(List.of(woodyMapping, floralMapping));
        when(woodyMapping.getVariant()).thenReturn(woodyVariant);
        when(floralMapping.getVariant()).thenReturn(floralVariant);
        when(woodyVariant.getProduct()).thenReturn(product);
        when(floralVariant.getProduct()).thenReturn(product);

        ProductOptionUpdateRequest request = new ProductOptionUpdateRequest(List.of(
                new ProductOptionGroupRequest(4L, "용량", 0,
                        List.of(new ProductOptionValueRequest(5L, "30ml", 0)))
        ));

        productOptionService.retireVariantsUsingRemovedOptions(1L, 3L, request);

        verify(woodyVariant).deactivate();
        verify(floralVariant).deactivate();
        verify(mappingRepository).deleteAll(List.of(woodyMapping, floralMapping));
        verify(mappingRepository).flush();
    }

    @Test
    void lifecycleMutationMethodsHaveTransactionalBoundary() throws Exception {
        assertThat(ProductOptionService.class
                .getMethod("retireVariantsUsingRemovedOptions", Long.class, Long.class, ProductOptionUpdateRequest.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(ProductModificationService.class
                .getMethod("modifyProduct", Long.class, Long.class,
                        com.giftmarket.product.dto.request.ProductModificationRequest.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void orderItemKeepsDirectVariantReferenceAndIndependentOptionSnapshot() throws Exception {
        var variantField = OrderItem.class.getDeclaredField("variant");
        var snapshotField = OrderItem.class.getDeclaredField("optionSnapshot");

        assertThat(variantField.getType()).isEqualTo(ProductVariant.class);
        assertThat(variantField.getAnnotation(JoinColumn.class).name()).isEqualTo("variant_id");
        assertThat(snapshotField.getType()).isEqualTo(String.class);
    }
}
