package com.giftmarket.user.service;

import com.giftmarket.address.repository.AddressRepository;
import com.giftmarket.auth.repository.RefreshTokenRepository;
import com.giftmarket.cart.repository.CartItemRepository;
import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserStatus;
import com.giftmarket.user.repository.UserRepository;
import com.giftmarket.wishlist.repository.WishlistItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceWithdrawalTest {
    @Mock UserRepository users;
    @Mock StorageService storage;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock AddressRepository addresses;
    @Mock CartItemRepository cartItems;
    @Mock WishlistItemRepository wishlists;
    @Mock OrderRepository orders;
    @Mock OrderCancellationRepository cancellations;
    @Mock ReturnRequestRepository returns;
    @Mock ExchangeRequestRepository exchanges;
    @Mock SellerRepository sellers;
    @Mock SellerOrderRepository sellerOrders;

    private UserService service() {
        return new UserService(users, storage, refreshTokens, addresses, cartItems,
                wishlists, orders, cancellations, returns, exchanges, sellers, sellerOrders);
    }

    @Test
    void withdrawsAndAnonymizesUserAndDeletesSessionData() {
        User user = User.createOAuthUser("user@example.com", "홍길동", null,
                com.giftmarket.user.entity.AuthProvider.GOOGLE, "google-sub");
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(sellers.findByUserIdForUpdate(7L)).thenReturn(Optional.empty());

        service().withdraw(7L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isNotNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getName()).isEqualTo("탈퇴회원");
        assertThat(user.getProviderId()).startsWith("withdrawn-7-");
        verify(refreshTokens).deleteByUserId(7L);
        verify(addresses).deleteAllByUserId(7L);
        verify(cartItems).deleteAllByUserId(7L);
        verify(wishlists).deleteAllByUserId(7L);
    }

    @Test
    void blocksWithdrawalWhenClaimIsProcessing() {
        User user = User.createOAuthUser("user@example.com", "홍길동", null,
                com.giftmarket.user.entity.AuthProvider.KAKAO, "kakao-id");
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(cancellations.existsByOrderUserIdAndStatusIn(eq(7L), any())).thenReturn(true);

        assertThatThrownBy(() -> service().withdraw(7L))
                .hasMessageContaining("진행 중인 주문 또는 클레임");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verifyNoInteractions(refreshTokens, addresses, cartItems, wishlists);
    }

    @Test
    void withdrawsSellerAfterSellerWorkIsComplete() {
        User user = User.createOAuthUser("seller@example.com", "판매자", null,
                com.giftmarket.user.entity.AuthProvider.GOOGLE, "google-seller");
        Seller seller = mock(Seller.class);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(sellers.findByUserIdForUpdate(7L)).thenReturn(Optional.of(seller));
        when(seller.getId()).thenReturn(11L);

        service().withdraw(7L);

        verify(seller).withdraw();
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    }
}
