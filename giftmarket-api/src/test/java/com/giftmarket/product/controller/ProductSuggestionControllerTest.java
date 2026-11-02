package com.giftmarket.product.controller;

import com.giftmarket.product.dto.response.ProductSuggestionsResponse;
import com.giftmarket.product.search.ProductSearchService;
import com.giftmarket.product.service.ProductService;
import com.giftmarket.product.service.ProductSuggestionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductSuggestionControllerTest {
    @Test
    void wrapsSuggestionDataInApiResponse() throws Exception {
        var suggestions = mock(ProductSuggestionService.class);
        when(suggestions.suggest("선물")).thenReturn(new ProductSuggestionsResponse(
                List.of(new ProductSuggestionsResponse.Item(5L, "선물 세트", "https://example.test/image", 1000L)), List.of("선물 세트")));
        var mvc = MockMvcBuilders.standaloneSetup(new ProductController(
                mock(ProductService.class), mock(ProductSearchService.class), suggestions)).build();
        mvc.perform(get("/api/products/suggest").param("q", "선물"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.products[0].id").value(5))
                .andExpect(jsonPath("$.data.products[0].imageUrl").value("https://example.test/image"))
                .andExpect(jsonPath("$.data.keywords[0]").value("선물 세트"));
    }
}
