package com.giftmarket.product.service;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.exception.ProductException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProductSkuPolicyTest {
    @Test
    void newVariantsAcrossProductsAndSellersAlwaysGetIndependentSystemCodes() {
        ProductVariant a = ProductVariant.create(mock(Product.class), " f1 ", "a", 0L, 5);
        ProductVariant b = ProductVariant.create(mock(Product.class), "F1", "b", 0L, 7);
        assertThat(a.getSellerSkuCode()).isEqualTo("F1");
        assertThat(b.getSellerSkuCode()).isEqualTo("F1");
        assertThat(a.getSkuCode()).isNotEqualTo(b.getSkuCode());
        assertThatCode(() -> UUID.fromString(a.getSkuCode().substring(3))).doesNotThrowAnyException();
    }

    @Test
    void editDeactivateReactivateAndNewRegistrationKeepTheirOwnSystemIdentities() {
        ProductVariant a = ProductVariant.create(mock(Product.class), "F1", "a", 0L, 5);
        String sku = a.getSkuCode();
        a.update(" red_%-- ", "a", 100L);
        a.deactivate();
        a.activate();
        assertThat(a.getSkuCode()).isEqualTo(sku);
        assertThat(a.getSellerSkuCode()).isEqualTo("RED_%--");
        assertThat(a.getStockQuantity()).isEqualTo(5);
        ProductVariant newVariant = ProductVariant.create(mock(Product.class), "F1", "b", 0L, 9);
        assertThat(newVariant.getSkuCode()).isNotEqualTo(sku);
    }

    @Test
    void validatesNormalizedLengthAndBlank() {
        assertThatThrownBy(() -> ProductVariant.normalizeSellerSkuCode(" "))
                .isInstanceOf(ProductException.class);
        assertThatThrownBy(() -> ProductVariant.normalizeSellerSkuCode("ß".repeat(51)))
                .isInstanceOf(ProductException.class);
    }
}
