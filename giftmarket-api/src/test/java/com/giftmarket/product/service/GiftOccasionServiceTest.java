package com.giftmarket.product.service;

import com.giftmarket.product.dto.request.GiftOccasionActiveUpdateRequest;
import com.giftmarket.product.dto.request.GiftOccasionCreateRequest;
import com.giftmarket.product.dto.request.GiftOccasionUpdateRequest;
import com.giftmarket.product.entity.GiftOccasion;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductGiftOccasion;
import com.giftmarket.product.exception.ProductException;
import com.giftmarket.product.repository.GiftOccasionRepository;
import com.giftmarket.product.repository.ProductGiftOccasionRepository;
import com.giftmarket.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GiftOccasionServiceTest {

    @Mock GiftOccasionRepository giftOccasionRepository;
    @Mock ProductGiftOccasionRepository productGiftOccasionRepository;
    @Mock ProductRepository productRepository;

    GiftOccasionService service;

    @BeforeEach
    void setUp() {
        service = new GiftOccasionService(
                giftOccasionRepository,
                productGiftOccasionRepository,
                productRepository
        );
    }

    @Test
    void createsGiftOccasion() {
        given(giftOccasionRepository.existsByNameIgnoreCase("생일")).willReturn(false);
        given(giftOccasionRepository.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        var response = service.createGiftOccasion(new GiftOccasionCreateRequest(" 생일 ", 1));

        assertThat(response.name()).isEqualTo("생일");
        assertThat(response.active()).isTrue();
    }

    @Test
    void rejectsDuplicateName() {
        given(giftOccasionRepository.existsByNameIgnoreCase("생일")).willReturn(true);
        assertThatThrownBy(() -> service.createGiftOccasion(new GiftOccasionCreateRequest("생일", 1)))
                .isInstanceOf(ProductException.class);
        verify(giftOccasionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updatesGiftOccasionAndActiveState() {
        GiftOccasion occasion = occasion(1L, "생일", 1);
        given(giftOccasionRepository.findById(1L)).willReturn(Optional.of(occasion));
        given(giftOccasionRepository.existsByNameIgnoreCaseAndIdNot("생일 축하", 1L)).willReturn(false);

        var updated = service.updateGiftOccasion(1L, new GiftOccasionUpdateRequest("생일 축하", 3));
        var inactive = service.updateActive(1L, new GiftOccasionActiveUpdateRequest(false));

        assertThat(updated.name()).isEqualTo("생일 축하");
        assertThat(updated.displayOrder()).isEqualTo(3);
        assertThat(inactive.active()).isFalse();
    }

    @Test
    void synchronizesZeroOneAndThreeOccasions() {
        Product product = newProduct(10L);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));
        service.synchronizeProductGiftOccasions(10L, List.of());

        List<GiftOccasion> occasions = List.of(
                occasion(1L, "생일", 1), occasion(2L, "감사", 2), occasion(3L, "축하", 3)
        );
        given(giftOccasionRepository.findAllByIdInAndActiveTrue(List.of(1L))).willReturn(occasions.subList(0, 1));
        service.synchronizeProductGiftOccasions(10L, List.of(1L));
        given(giftOccasionRepository.findAllByIdInAndActiveTrue(List.of(1L, 2L, 3L))).willReturn(occasions);
        service.synchronizeProductGiftOccasions(10L, List.of(1L, 2L, 3L));

        verify(productGiftOccasionRepository, org.mockito.Mockito.times(3)).deleteAllByProductId(10L);
    }

    @Test
    void rejectsFourDuplicateMissingAndInactiveOccasions() {
        assertThatThrownBy(() -> service.synchronizeProductGiftOccasions(10L, List.of(1L, 2L, 3L, 4L)))
                .isInstanceOf(ProductException.class);
        assertThatThrownBy(() -> service.synchronizeProductGiftOccasions(10L, List.of(1L, 1L)))
                .isInstanceOf(ProductException.class);

        given(giftOccasionRepository.findAllByIdInAndActiveTrue(List.of(99L))).willReturn(List.of());
        assertThatThrownBy(() -> service.synchronizeProductGiftOccasions(10L, List.of(99L)))
                .isInstanceOf(ProductException.class);

        given(giftOccasionRepository.findAllByIdInAndActiveTrue(List.of(2L))).willReturn(List.of());
        assertThatThrownBy(() -> service.synchronizeProductGiftOccasions(10L, List.of(2L)))
                .isInstanceOf(ProductException.class);
    }

    @Test
    void returnsOnlyActiveRelationsAndReplacingRemovesInactiveExistingRelation() {
        GiftOccasion active = occasion(1L, "생일", 1);
        Product product = newProduct(10L);
        given(productGiftOccasionRepository.findActiveByProductId(10L))
                .willReturn(List.of(ProductGiftOccasion.create(product, active)));
        assertThat(service.getActiveProductGiftOccasions(10L)).extracting("name").containsExactly("생일");

        given(giftOccasionRepository.findAllByIdInAndActiveTrue(List.of(1L))).willReturn(List.of(active));
        given(productRepository.findById(10L)).willReturn(Optional.of(product));
        service.synchronizeProductGiftOccasions(10L, List.of(1L));

        verify(productGiftOccasionRepository).deleteAllByProductId(10L);
        ArgumentCaptor<List<ProductGiftOccasion>> captor = ArgumentCaptor.forClass(List.class);
        verify(productGiftOccasionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void returnsActiveOccasionsGroupedByProductForSellerList() {
        Product firstProduct = org.mockito.Mockito.mock(Product.class);
        Product secondProduct = org.mockito.Mockito.mock(Product.class);
        given(firstProduct.getId()).willReturn(10L);
        given(secondProduct.getId()).willReturn(20L);

        GiftOccasion birthday = occasion(1L, "생일", 1);
        GiftOccasion thanks = occasion(2L, "감사", 2);
        given(productGiftOccasionRepository.findActiveByProductIdIn(List.of(10L, 20L)))
                .willReturn(List.of(
                        ProductGiftOccasion.create(firstProduct, birthday),
                        ProductGiftOccasion.create(firstProduct, thanks),
                        ProductGiftOccasion.create(secondProduct, thanks)
                ));

        var result = service.getActiveProductGiftOccasions(List.of(10L, 20L));

        assertThat(result.get(10L)).extracting("name").containsExactly("생일", "감사");
        assertThat(result.get(20L)).extracting("name").containsExactly("감사");
    }

    private GiftOccasion occasion(Long id, String name, int order) {
        GiftOccasion occasion = GiftOccasion.create(name, order);
        ReflectionTestUtils.setField(occasion, "id", id);
        return occasion;
    }

    private Product newProduct(Long id) {
        return org.mockito.Mockito.mock(Product.class);
    }
}
