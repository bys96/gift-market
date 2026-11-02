package com.giftmarket.product.service;

import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductSuggestionServiceTest {
    @Test
    void emptyAndOverlongQueriesDoNotReachDatabase() {
        var repository = mock(ProductRepository.class);
        var service = new ProductSuggestionService(repository, mock(StorageService.class));
        assertThat(service.suggest("  ").products()).isEmpty();
        assertThat(service.suggest("가".repeat(101)).keywords()).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void limitsAndEscapesQueryAndReturnsImageAndDistinctNames() {
        var repository = mock(ProductRepository.class);
        var storage = mock(StorageService.class);
        var product = mock(Product.class);
        when(product.getId()).thenReturn(4L);
        when(product.getName()).thenReturn("초콜릿");
        when(product.getPrice()).thenReturn(12000L);
        when(product.getRepresentativeImageKey()).thenReturn("item/image.jpg");
        when(storage.createReadUrl("item/image.jpg")).thenReturn("https://example.test/image.jpg");
        when(repository.findSuggestionProducts("초!%!_!!", PageRequest.of(0, 5))).thenReturn(List.of(product, product));
        var result = new ProductSuggestionService(repository, storage).suggest(" 초%_! ");
        assertThat(result.products()).hasSize(2);
        assertThat(result.products().getFirst().imageUrl()).isEqualTo("https://example.test/image.jpg");
        assertThat(result.products().getFirst().price()).isEqualTo(12000L);
        assertThat(result.keywords()).containsExactly("초콜릿");
    }
}
