package com.giftmarket.cart.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.cart.dto.request.CartItemCreateRequest;
import com.giftmarket.cart.dto.request.CartItemQuantityUpdateRequest;
import com.giftmarket.cart.dto.response.CartItemResponse;
import com.giftmarket.cart.dto.response.CartResponse;
import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.cart.exception.CartException;
import com.giftmarket.cart.repository.CartItemRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public CartResponse addCartItem(
            Long userId,
            CartItemCreateRequest request
    ) {
        User user = getAuthenticatedUser(userId);
        Product product = getAvailableProduct(request.productId());

        validateQuantity(
                request.quantity(),
                product.getStockQuantity()
        );

        CartItem cartItem = cartItemRepository
                .findByUserIdAndProductId(
                        user.getId(),
                        product.getId()
                )
                .map(existingCartItem -> {
                    int nextQuantity =
                            existingCartItem.getQuantity()
                                    + request.quantity();

                    validateQuantity(
                            nextQuantity,
                            product.getStockQuantity()
                    );

                    existingCartItem.increaseQuantity(
                            request.quantity(),
                            product.getStockQuantity()
                    );

                    return existingCartItem;
                })
                .orElseGet(() -> cartItemRepository.save(
                        CartItem.create(
                                user,
                                product,
                                request.quantity()
                        )
                ));

        return getCart(userId);
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        validateAuthentication(userId);

        List<CartItemResponse> items = cartItemRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(CartItemResponse::from)
                .toList();

        return CartResponse.from(items);
    }

    @Transactional
    public CartResponse updateQuantity(
            Long userId,
            Long cartItemId,
            CartItemQuantityUpdateRequest request
    ) {
        validateAuthentication(userId);

        CartItem cartItem = getMyCartItem(
                userId,
                cartItemId
        );

        Product product = cartItem.getProduct();

        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new CartException(
                    "현재 판매 중인 상품이 아닙니다."
            );
        }

        validateQuantity(
                request.quantity(),
                product.getStockQuantity()
        );

        cartItem.changeQuantity(
                request.quantity()
        );

        return getCart(userId);
    }

    @Transactional
    public CartResponse deleteCartItem(
            Long userId,
            Long cartItemId
    ) {
        validateAuthentication(userId);

        CartItem cartItem = getMyCartItem(
                userId,
                cartItemId
        );

        cartItemRepository.delete(cartItem);

        return getCart(userId);
    }

    @Transactional
    public void clearCart(Long userId) {
        validateAuthentication(userId);

        cartItemRepository.deleteAllByUserId(userId);
    }

    private User getAuthenticatedUser(Long userId) {
        validateAuthentication(userId);

        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException(
                        "사용자 정보를 찾을 수 없습니다."
                ));
    }

    private void validateAuthentication(Long userId) {
        if (userId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }
    }

    private Product getAvailableProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CartException(
                        "상품을 찾을 수 없습니다."
                ));

        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new CartException(
                    "현재 판매 중인 상품이 아닙니다."
            );
        }

        if (product.getStockQuantity() <= 0) {
            throw new CartException(
                    "품절된 상품입니다."
            );
        }

        return product;
    }

    private CartItem getMyCartItem(
            Long userId,
            Long cartItemId
    ) {
        return cartItemRepository
                .findByIdAndUserId(
                        cartItemId,
                        userId
                )
                .orElseThrow(() -> new CartException(
                        "장바구니 상품을 찾을 수 없습니다."
                ));
    }

    private void validateQuantity(
            Integer quantity,
            Integer stockQuantity
    ) {
        if (quantity == null || quantity <= 0) {
            throw new CartException(
                    "수량은 1개 이상이어야 합니다."
            );
        }

        if (quantity > stockQuantity) {
            throw new CartException(
                    "상품 재고보다 많은 수량을 담을 수 없습니다."
            );
        }
    }
}