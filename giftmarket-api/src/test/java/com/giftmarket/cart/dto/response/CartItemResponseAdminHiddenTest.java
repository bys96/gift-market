package com.giftmarket.cart.dto.response;

import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.seller.entity.Seller;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CartItemResponseAdminHiddenTest {

    @Test
    void existingAdminHiddenCartItemIsMarkedNotPurchasable() {
        CartItem cartItem = mock(CartItem.class);
        Product product = mock(Product.class);
        Seller seller = mock(Seller.class);
        given(cartItem.getProduct()).willReturn(product);
        given(cartItem.getQuantity()).willReturn(1);
        given(product.getSeller()).willReturn(seller);
        given(product.getPrice()).willReturn(10_000L);
        given(product.getStockQuantity()).willReturn(10);
        given(product.getStatus()).willReturn(ProductStatus.ON_SALE);
        given(product.isAdminHidden()).willReturn(true);

        CartItemResponse response = CartItemResponse.from(cartItem, List.of());

        assertThat(response.isPurchasable()).isFalse();
        assertThat(response.getAvailability()).isEqualTo(CartItemAvailability.SALE_STOPPED);
    }
}
