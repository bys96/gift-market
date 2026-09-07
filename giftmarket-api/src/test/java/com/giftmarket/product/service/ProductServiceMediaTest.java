package com.giftmarket.product.service;

import com.giftmarket.product.draft.repository.ProductDraftRepository;
import com.giftmarket.product.dto.request.ProductCreateRequest;
import com.giftmarket.product.dto.request.ProductUpdateRequest;
import com.giftmarket.product.entity.Category;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.*;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ProductServiceMediaTest {
    static Stream<String> descriptions() {
        String id = "12345678-1234-1234-1234-123456789abc";
        return Stream.of(
                "<img src=\"https://legacy.example/detail.png\" alt=\"기존 이미지\">",
                "<img data-storage-key=\"products/12/content/" + id + ".png\">",
                "<video data-storage-key=\"products/12/content/video/" + id + ".mp4\" controls preload=\"metadata\"></video>"
        );
    }

    @ParameterizedTest
    @MethodSource("descriptions")
    void mediaOnlyDescriptionCanStartSaleAndBeEditedWithoutChangingImageKeys(String description) {
        ProductRepository products = mock(ProductRepository.class);
        ProductImageRepository images = mock(ProductImageRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        SellerRepository sellers = mock(SellerRepository.class);
        Seller seller = mock(Seller.class);
        Category category = mock(Category.class);
        when(seller.getId()).thenReturn(12L);
        when(seller.getStatus()).thenReturn(SellerStatus.ACTIVE);
        when(category.getId()).thenReturn(3L);
        when(category.getName()).thenReturn("테스트 카테고리");
        when(sellers.findByUserId(7L)).thenReturn(Optional.of(seller));
        when(categories.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(category));
        AtomicReference<Product> saved = new AtomicReference<>();
        when(products.save(any())).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 42L);
            saved.set(product);
            return product;
        });
        when(products.findByIdAndSellerIdAndDeletedAtIsNull(42L, 12L)).thenAnswer(invocation -> Optional.of(saved.get()));
        when(images.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        ProductService service = new ProductService(products, images, mock(ProductDraftRepository.class),
                mock(ProductOptionGroupRepository.class), mock(ProductOptionValueRepository.class),
                mock(ProductVariantRepository.class), mock(ProductVariantOptionValueRepository.class),
                categories, sellers, new ProductDescriptionSanitizer());
        String representative = "products/12/representative/main.png";
        List<String> gallery = List.of("products/12/gallery/first.png");
        var created = service.createProduct(7L, new ProductCreateRequest(3L, "상품", null, null,
                description, 1000L, 5, representative, gallery, true, 0L, 3, 3000L, 6000L, true));
        assertThat(created.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(created.getDescription()).isNotBlank();
        assertThat(created.getRepresentativeImageKey()).isEqualTo(representative);
        assertThat(created.getGalleryImageKeys()).isEqualTo(gallery);

        var updated = service.updateProduct(7L, 42L, new ProductUpdateRequest(3L, "수정 상품", null, null,
                created.getDescription(), 1000L, 5, representative, gallery, true, 0L, 3, 3000L, 6000L));
        assertThat(updated.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(updated.getDescription()).isEqualTo(created.getDescription());
        assertThat(updated.getRepresentativeImageKey()).isEqualTo(representative);
        assertThat(updated.getGalleryImageKeys()).isEqualTo(gallery);
    }
}
