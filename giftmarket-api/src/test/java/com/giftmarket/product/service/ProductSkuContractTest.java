package com.giftmarket.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftmarket.product.dto.request.ProductVariantRequest;
import com.giftmarket.product.dto.response.ProductVariantResponse;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProductSkuContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requiresExplicitSellerCodeAndRejectsLegacyRegistrationAndModification() throws Exception {
        for (Class<?> type : List.of(ProductVariantRequest.class,
                com.giftmarket.product.dto.request.ProductRegistrationVariantRequest.class,
                com.giftmarket.product.dto.request.ProductModificationVariantRequest.class)) {
            assertThatThrownBy(() -> mapper.readValue("{\"skuCode\":\"GM-system\"}", type))
                    .hasRootCauseInstanceOf(com.giftmarket.product.exception.ProductConflictException.class);
            assertThatThrownBy(() -> mapper.readValue("{}", type))
                    .hasRootCauseInstanceOf(com.giftmarket.product.exception.ProductConflictException.class);
            Object request = mapper.readValue("{\"sellerSkuCode\":\"F1\"}", type);
            assertThat(mapper.writeValueAsString(request)).contains("\"sellerSkuCode\":\"F1\"");
        }
    }

    @Test
    void responseSeparatesSellerAndSystemValues() throws Exception {
        ProductVariant variant = ProductVariant.create(mock(Product.class), "F1", "a", 0L, 3);
        String json = mapper.writeValueAsString(ProductVariantResponse.from(variant, List.of()));
        assertThat(json).contains("\"sellerSkuCode\":\"F1\"", "\"skuCode\":\"GM-");
    }
}
