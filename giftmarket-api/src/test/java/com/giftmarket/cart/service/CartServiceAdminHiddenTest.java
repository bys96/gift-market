package com.giftmarket.cart.service;

import com.giftmarket.cart.dto.request.CartItemCreateRequest;
import com.giftmarket.cart.exception.CartException;
import com.giftmarket.cart.repository.CartItemRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.repository.ProductOptionGroupRepository;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.product.repository.ProductVariantOptionValueRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CartServiceAdminHiddenTest {

    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductOptionGroupRepository optionGroupRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock ProductVariantOptionValueRepository variantOptionValueRepository;
    @Mock UserRepository userRepository;
    @Mock User user;
    @Mock Product product;
    @Mock Seller seller;

    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(
                cartItemRepository,
                productRepository,
                optionGroupRepository,
                variantRepository,
                variantOptionValueRepository,
                userRepository
        );
    }

    @Test
    void cannotAddAdminHiddenProduct() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(productRepository.findById(10L)).willReturn(Optional.of(product));
        given(product.isDeleted()).willReturn(false);
        given(product.isAdminHidden()).willReturn(true);

        assertThatThrownBy(() -> service.addCartItem(
                1L,
                new CartItemCreateRequest(10L, null, 1)
        )).isInstanceOf(CartException.class);

        verify(cartItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cannotAddSalesSuspendedSellerProduct() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(productRepository.findById(10L)).willReturn(Optional.of(product));
        given(product.isDeleted()).willReturn(false);
        given(product.isAdminHidden()).willReturn(false);
        given(product.getSeller()).willReturn(seller);
        given(seller.getStatus()).willReturn(SellerStatus.SALES_SUSPENDED);

        assertThatThrownBy(() -> service.addCartItem(1L, new CartItemCreateRequest(10L, null, 1)))
                .isInstanceOf(CartException.class);

        verify(cartItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
