package com.giftmarket.wishlist.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.product.dto.response.ProductSummaryResponse;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import com.giftmarket.wishlist.entity.WishlistItem;
import com.giftmarket.wishlist.exception.WishlistException;
import com.giftmarket.wishlist.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private static final List<ProductStatus> ADDABLE_STATUSES = List.of(
            ProductStatus.ON_SALE,
            ProductStatus.SOLD_OUT
    );

    private final WishlistItemRepository wishlistItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public List<ProductSummaryResponse> getWishlist(Long userId) {
        validateAuthentication(userId);

        return wishlistItemRepository.findVisibleByUserId(userId)
                .stream()
                .map(WishlistItem::getProduct)
                .map(ProductSummaryResponse::from)
                .toList();
    }

    @Transactional
    public ProductSummaryResponse addWishlist(Long userId, Long productId) {
        validateAuthentication(userId);

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AuthenticationException(
                        "사용자 정보를 찾을 수 없습니다."
                ));

        Product product = productRepository
                .findByIdAndStatusInAndDeletedAtIsNull(productId, ADDABLE_STATUSES)
                .orElseThrow(() -> new WishlistException(
                        "찜할 수 있는 상품을 찾을 수 없습니다."
                ));

        if (!wishlistItemRepository.existsByUserIdAndProductId(userId, productId)) {
            wishlistItemRepository.save(WishlistItem.create(user, product));
        }

        return ProductSummaryResponse.from(product);
    }

    @Transactional
    public void removeWishlist(Long userId, Long productId) {
        validateAuthentication(userId);
        wishlistItemRepository.deleteByUserIdAndProductId(userId, productId);
    }

    public long countWishlist(Long userId) {
        validateAuthentication(userId);
        return wishlistItemRepository.countVisibleByUserId(userId);
    }

    private void validateAuthentication(Long userId) {
        if (userId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }
    }
}
