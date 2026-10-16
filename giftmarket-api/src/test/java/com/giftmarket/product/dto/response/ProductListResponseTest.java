package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.Category;
import com.giftmarket.product.entity.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductListResponseTest {

    @Test
    void defaultsAndSerializesGiftOccasionsAsEmptyList() throws Exception {
        Product product = mock(Product.class);
        Category category = mock(Category.class);
        when(product.getCategory()).thenReturn(category);

        ProductListResponse response = ProductListResponse.from(product);

        assertThat(response.getGiftOccasions()).isNotNull().isEmpty();
        assertThat(new ObjectMapper().writeValueAsString(response))
                .contains("\"giftOccasions\":[]");
    }
}
