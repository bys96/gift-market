package com.giftmarket.wishlist.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.product.entity.Category;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import com.giftmarket.wishlist.entity.WishlistItem;
import com.giftmarket.wishlist.exception.WishlistException;
import com.giftmarket.wishlist.repository.WishlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    @Mock WishlistItemRepository wishlistItemRepository;
    @Mock UserRepository userRepository;
    @Mock ProductRepository productRepository;
    @Mock User user;
    @Mock Product product;
    @Mock Category category;
    @Mock WishlistItem wishlistItem;

    private WishlistService service;

    @BeforeEach
    void setUp() {
        service = new WishlistService(
                wishlistItemRepository,
                userRepository,
                productRepository
        );
    }

    @Test
    void addsWishlistForAuthenticatedUser() {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        givenAddableProduct();
        given(wishlistItemRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID))
                .willReturn(false);

        var response = service.addWishlist(USER_ID, PRODUCT_ID);

        assertThat(response.getId()).isEqualTo(PRODUCT_ID);
        verify(wishlistItemRepository).save(any(WishlistItem.class));
    }

    @Test
    void duplicateAddIsIdempotent() {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        givenAddableProduct();
        given(wishlistItemRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID))
                .willReturn(true);

        service.addWishlist(USER_ID, PRODUCT_ID);

        verify(wishlistItemRepository, never()).save(any());
    }

    @Test
    void rejectsMissingOrNonBuyerVisibleProduct() {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(productRepository.findByIdAndStatusInAndDeletedAtIsNull(eq(PRODUCT_ID), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.addWishlist(USER_ID, PRODUCT_ID))
                .isInstanceOf(WishlistException.class);
    }

    @Test
    void removeUsesCurrentUserAndProductOnly() {
        service.removeWishlist(USER_ID, PRODUCT_ID);

        verify(wishlistItemRepository).deleteByUserIdAndProductId(USER_ID, PRODUCT_ID);
    }

    @Test
    void removingMissingWishlistIsIdempotent() {
        given(wishlistItemRepository.deleteByUserIdAndProductId(USER_ID, PRODUCT_ID))
                .willReturn(0L);

        service.removeWishlist(USER_ID, PRODUCT_ID);

        verify(wishlistItemRepository).deleteByUserIdAndProductId(USER_ID, PRODUCT_ID);
    }

    @Test
    void listsOnlyRepositoryRowsForCurrentUser() {
        givenProductSummary(ProductStatus.ON_SALE, 12_000L, "latest.jpg");
        given(wishlistItem.getProduct()).willReturn(product);
        given(wishlistItemRepository.findVisibleByUserId(USER_ID))
                .willReturn(List.of(wishlistItem));

        var response = service.getWishlist(USER_ID);

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(PRODUCT_ID);
            assertThat(item.getPrice()).isEqualTo(12_000L);
            assertThat(item.getRepresentativeImageKey()).isEqualTo("latest.jpg");
        });
        verify(wishlistItemRepository).findVisibleByUserId(USER_ID);
    }

    @Test
    void listReflectsCurrentSoldOutStatus() {
        givenProductSummary(ProductStatus.SOLD_OUT, 15_000L, null);
        given(wishlistItem.getProduct()).willReturn(product);
        given(wishlistItemRepository.findVisibleByUserId(USER_ID))
                .willReturn(List.of(wishlistItem));

        assertThat(service.getWishlist(USER_ID).getFirst().getStatus())
                .isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    void listReflectsCurrentHiddenStatus() {
        givenProductSummary(ProductStatus.HIDDEN, 15_000L, null);
        given(wishlistItem.getProduct()).willReturn(product);
        given(wishlistItemRepository.findVisibleByUserId(USER_ID))
                .willReturn(List.of(wishlistItem));

        assertThat(service.getWishlist(USER_ID).getFirst().getStatus())
                .isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    void deletedProductIsHiddenByRepositoryContract() {
        given(wishlistItemRepository.findVisibleByUserId(USER_ID)).willReturn(List.of());

        assertThat(service.getWishlist(USER_ID)).isEmpty();
    }

    @Test
    void countUsesVisibleRowsForCurrentUser() {
        given(wishlistItemRepository.countVisibleByUserId(USER_ID)).willReturn(3L);

        assertThat(service.countWishlist(USER_ID)).isEqualTo(3L);
    }

    @Test
    void userDataIsSeparatedByAuthenticatedUserId() {
        service.getWishlist(2L);

        verify(wishlistItemRepository).findVisibleByUserId(2L);
        verify(wishlistItemRepository, never()).findVisibleByUserId(USER_ID);
    }

    @Test
    void rejectsUnauthenticatedOwnershipOperations() {
        assertThatThrownBy(() -> service.getWishlist(null))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> service.addWishlist(null, PRODUCT_ID))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> service.removeWishlist(null, PRODUCT_ID))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> service.countWishlist(null))
                .isInstanceOf(AuthenticationException.class);
    }

    private void givenAddableProduct() {
        givenProductSummary(ProductStatus.ON_SALE, 10_000L, "image.jpg");
        given(productRepository.findByIdAndStatusInAndDeletedAtIsNull(eq(PRODUCT_ID), any()))
                .willReturn(Optional.of(product));
    }

    private void givenProductSummary(
            ProductStatus status,
            long price,
            String representativeImageKey
    ) {
        given(product.getId()).willReturn(PRODUCT_ID);
        given(product.getCategory()).willReturn(category);
        given(category.getId()).willReturn(5L);
        given(category.getName()).willReturn("선물");
        given(product.getName()).willReturn("최신 상품명");
        given(product.getPrice()).willReturn(price);
        given(product.getStatus()).willReturn(status);
        given(product.getRepresentativeImageKey()).willReturn(representativeImageKey);
        given(product.getShippingFee()).willReturn(0L);
    }
}
