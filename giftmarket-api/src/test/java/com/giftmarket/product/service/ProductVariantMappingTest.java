package com.giftmarket.product.service;

import com.giftmarket.product.entity.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductVariantMappingTest {
    @Test
    void requiresExactUniqueMappingSetsIndependentOfOrder() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        ProductVariant variant = mock(ProductVariant.class);
        when(variant.getId()).thenReturn(10L);
        when(variant.getProduct()).thenReturn(product);
        when(variant.getCombinationKey()).thenReturn("2:31|1:21");
        var first = mapping(product, variant, 1L, 21L);
        var second = mapping(product, variant, 2L, 31L);
        assertThat(ProductVariantMapping.isValid(variant, List.of(first, second))).isTrue();
        assertThat(ProductVariantMapping.isValid(variant, List.of())).isFalse();
        assertThat(ProductVariantMapping.isValid(variant, List.of(second))).isFalse();
        assertThat(ProductVariantMapping.isValid(variant, List.of(first, second, second))).isFalse();
        assertThatThrownBy(() -> ProductVariantMapping.requireValid(variant, List.of(second)))
                .isInstanceOf(com.giftmarket.product.exception.ProductConflictException.class);
    }

    private ProductVariantOptionValue mapping(Product product, ProductVariant variant, Long groupId, Long valueId) {
        var group = mock(ProductOptionGroup.class);
        when(group.getId()).thenReturn(groupId);
        when(group.getProduct()).thenReturn(product);
        var value = mock(ProductOptionValue.class);
        when(value.getId()).thenReturn(valueId);
        when(value.getOptionGroup()).thenReturn(group);
        return ProductVariantOptionValue.create(variant, value);
    }
}
