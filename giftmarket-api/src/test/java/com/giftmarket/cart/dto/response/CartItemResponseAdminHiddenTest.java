package com.giftmarket.cart.dto.response;

import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.entity.Category;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
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
        given(seller.getStatus()).willReturn(SellerStatus.ACTIVE);
        given(product.getPrice()).willReturn(10_000L);
        given(product.getStockQuantity()).willReturn(10);
        given(product.getStatus()).willReturn(ProductStatus.ON_SALE);
        given(product.isAdminHidden()).willReturn(true);

        CartItemResponse response = CartItemResponse.from(cartItem, List.of());

        assertThat(response.isPurchasable()).isFalse();
        assertThat(response.getAvailability()).isEqualTo(CartItemAvailability.SALE_STOPPED);
    }

    @Test
    void existingSalesSuspendedSellerCartItemIsKeptButNotPurchasable() {
        CartItem cartItem = mock(CartItem.class);
        Product product = mock(Product.class);
        Seller seller = mock(Seller.class);
        given(cartItem.getProduct()).willReturn(product);
        given(cartItem.getQuantity()).willReturn(1);
        given(product.getSeller()).willReturn(seller);
        given(seller.getStatus()).willReturn(SellerStatus.SALES_SUSPENDED);
        given(product.getPrice()).willReturn(10_000L);
        given(product.getStockQuantity()).willReturn(10);

        CartItemResponse response = CartItemResponse.from(cartItem, List.of());

        assertThat(response.isPurchasable()).isFalse();
        assertThat(response.getAvailability()).isEqualTo(CartItemAvailability.SALE_STOPPED);
    }

    @Test
    void cartRequiresEverySellerAndProductRestrictionToBeReleased() {
        Seller seller = Seller.create(mock(com.giftmarket.user.entity.User.class), "선물 상점", null);
        Product product = Product.createDraft(
                seller, mock(Category.class), "상품", null, null, null,
                10_000L, 10, null, true, 0L, null, null, null
        );
        product.changeStatus(ProductStatus.ON_SALE);
        CartItem cartItem = mock(CartItem.class);
        given(cartItem.getProduct()).willReturn(product);
        given(cartItem.getQuantity()).willReturn(1);

        assertThat(CartItemResponse.from(cartItem, List.of()).isPurchasable()).isTrue();

        seller.suspendSales();
        assertThat(CartItemResponse.from(cartItem, List.of()).isPurchasable()).isFalse();
        seller.reactivateSales();
        assertThat(CartItemResponse.from(cartItem, List.of()).isPurchasable()).isTrue();

        product.hideByAdmin("관리자 제재");
        seller.suspendSales();
        seller.reactivateSales();
        assertThat(product.isAdminHidden()).isTrue();
        assertThat(CartItemResponse.from(cartItem, List.of()).isPurchasable()).isFalse();

        product.changeStatus(ProductStatus.HIDDEN);
        product.unhideByAdmin();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        assertThat(CartItemResponse.from(cartItem, List.of()).isPurchasable()).isFalse();

        product.changeStatus(ProductStatus.ON_SALE);
        assertThat(CartItemResponse.from(cartItem, List.of()).isPurchasable()).isTrue();
    }
}
