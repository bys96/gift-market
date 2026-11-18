package com.giftmarket.product.service;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.exception.ProductConflictException;
import com.giftmarket.product.repository.*;
import com.giftmarket.order.repository.OrderItemRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultVariantPolicyTest {
    @Test
    void conversionBlocksOpenExchangeReservationsAndRequiresExplicitAcknowledgement() {
        var variants = mock(ProductVariantRepository.class);
        var orders = mock(OrderItemRepository.class);
        var policy = new ProductInventoryPolicy(variants, mock(ProductVariantOptionValueRepository.class),
                mock(ProductOptionGroupRepository.class), orders, mock(ProductRepository.class));
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(10L);
        when(product.getStockQuantity()).thenReturn(5);
        ProductVariant defaultVariant = ProductVariant.createDefault(product);
        when(variants.findDefault(10L)).thenReturn(Optional.of(defaultVariant));
        when(orders.hasOpenExchange(10L)).thenReturn(true);
        assertThatThrownBy(() -> policy.requireConversion(10L, true)).isInstanceOf(ProductConflictException.class)
                .hasMessageContaining("교환");
        when(orders.hasOpenExchange(10L)).thenReturn(false);
        assertThatThrownBy(() -> policy.requireConversion(10L, false)).isInstanceOf(ProductConflictException.class)
                .hasMessageContaining("동의");
        assertThatCode(() -> policy.requireConversion(10L, true)).doesNotThrowAnyException();
    }
}
