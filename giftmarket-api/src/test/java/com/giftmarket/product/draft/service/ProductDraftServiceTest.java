package com.giftmarket.product.draft.service;

import com.giftmarket.product.draft.entity.ProductDraft;
import com.giftmarket.product.draft.repository.ProductDraftRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.exception.ProductException;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductDraftServiceTest {

    @Mock ProductDraftRepository productDraftRepository;
    @Mock ProductRepository productRepository;
    @Mock SellerRepository sellerRepository;

    ProductDraftService service;
    Seller seller;
    Product product;

    @BeforeEach
    void setUp() {
        service = new ProductDraftService(
                productDraftRepository,
                productRepository,
                sellerRepository
        );

        seller = mock(Seller.class);
        product = mock(Product.class);
        given(sellerRepository.findByUserId(7L)).willReturn(Optional.of(seller));
        given(seller.getStatus()).willReturn(SellerStatus.ACTIVE);
        given(seller.getId()).willReturn(12L);
    }

    @Test
    void returnsNullWhenModificationDraftDoesNotExist() {
        given(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(13L, 12L))
                .willReturn(Optional.of(product));
        given(productDraftRepository.findBySellerIdAndProductId(12L, 13L))
                .willReturn(Optional.empty());

        assertThat(service.getProductDraft(7L, 13L)).isNull();
    }

    @Test
    void returnsExistingModificationDraft() {
        ProductDraft draft = mock(ProductDraft.class);
        given(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(13L, 12L))
                .willReturn(Optional.of(product));
        given(productDraftRepository.findBySellerIdAndProductId(12L, 13L))
                .willReturn(Optional.of(draft));
        given(draft.getProduct()).willReturn(product);
        given(product.getId()).willReturn(13L);
        given(draft.getId()).willReturn(21L);
        given(draft.getDraftData()).willReturn("{}");

        var response = service.getProductDraft(7L, 13L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(21L);
        assertThat(response.productId()).isEqualTo(13L);
    }

    @Test
    void rejectsMissingOrAnotherSellersProductBeforeDraftLookup() {
        given(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(99L, 12L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProductDraft(7L, 99L))
                .isInstanceOf(ProductException.class);
        verify(productDraftRepository, never())
                .findBySellerIdAndProductId(12L, 99L);
    }
}
