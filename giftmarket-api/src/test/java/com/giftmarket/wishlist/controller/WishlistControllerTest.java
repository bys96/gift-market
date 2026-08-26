package com.giftmarket.wishlist.controller;

import com.giftmarket.product.dto.response.ProductSummaryResponse;
import com.giftmarket.wishlist.service.WishlistService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WishlistControllerTest {

    @Test
    void delegatesListAndCountToAuthenticatedUser() {
        WishlistService service = mock(WishlistService.class);
        WishlistController controller = new WishlistController(service);
        ProductSummaryResponse item = mock(ProductSummaryResponse.class);
        given(service.getWishlist(7L)).willReturn(List.of(item));
        given(service.countWishlist(7L)).willReturn(1L);

        assertThat(controller.getWishlist(7L).data()).containsExactly(item);
        assertThat(controller.countWishlist(7L).data()).isEqualTo(1L);
    }

    @Test
    void delegatesIdempotentMutationsWithCurrentUser() {
        WishlistService service = mock(WishlistService.class);
        WishlistController controller = new WishlistController(service);
        ProductSummaryResponse item = mock(ProductSummaryResponse.class);
        given(service.addWishlist(7L, 11L)).willReturn(item);

        assertThat(controller.addWishlist(7L, 11L).data()).isSameAs(item);
        assertThat(controller.removeWishlist(7L, 11L).success()).isTrue();
        verify(service).removeWishlist(7L, 11L);
    }
}
