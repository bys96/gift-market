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
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.entity.ProductVariantOptionValue;
import com.giftmarket.product.repository.ProductOptionGroupRepository;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.product.repository.ProductVariantOptionValueRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;

    private final ProductRepository productRepository;

    private final ProductOptionGroupRepository
            productOptionGroupRepository;

    private final ProductVariantRepository
            productVariantRepository;

    private final ProductVariantOptionValueRepository
            productVariantOptionValueRepository;

    private final UserRepository userRepository;

    @Transactional
    public CartResponse addCartItem(
            Long userId,
            CartItemCreateRequest request
    ) {
        User user = getAuthenticatedUser(userId);

        Product product =
                getAvailableProduct(request.productId());

        boolean hasOptions =
                hasProductOptions(product.getId());

        ProductVariant variant =
                resolveVariant(
                        product,
                        request.variantId(),
                        hasOptions
                );

        int stockQuantity =
                getStockQuantity(
                        product,
                        variant
                );

        validateQuantity(
                request.quantity(),
                stockQuantity
        );

        Optional<CartItem> existingCartItem =
                findExistingCartItem(
                        user.getId(),
                        product.getId(),
                        variant
                );

        if (existingCartItem.isPresent()) {
            CartItem cartItem =
                    existingCartItem.get();

            int nextQuantity =
                    cartItem.getQuantity()
                            + request.quantity();

            validateQuantity(
                    nextQuantity,
                    stockQuantity
            );

            cartItem.increaseQuantity(
                    request.quantity()
            );
        } else {
            cartItemRepository.save(
                    CartItem.create(
                            user,
                            product,
                            variant,
                            request.quantity()
                    )
            );
        }

        return getCart(userId);
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        validateAuthentication(userId);

        List<CartItem> cartItems =
                cartItemRepository
                        .findAllByUserIdOrderByCreatedAtDesc(
                                userId
                        )
                        .stream()
                        .filter(cartItem ->
                                !cartItem
                                        .getProduct()
                                        .isDeleted()
                        )
                        .toList();

        if (cartItems.isEmpty()) {
            return CartResponse.from(List.of());
        }

        List<Long> variantIds =
                cartItems.stream()
                        .map(CartItem::getVariant)
                        .filter(variant -> variant != null)
                        .map(ProductVariant::getId)
                        .toList();

        Map<Long, List<ProductVariantOptionValue>>
                optionValuesByVariantId =
                getOptionValuesByVariantId(
                        variantIds
                );

        List<CartItemResponse> items =
                cartItems.stream()
                        .map(cartItem -> {
                            ProductVariant variant =
                                    cartItem.getVariant();

                            List<ProductVariantOptionValue>
                                    optionValues =
                                    variant == null
                                            ? List.of()
                                            : optionValuesByVariantId
                                            .getOrDefault(
                                                    variant.getId(),
                                                    List.of()
                                            );

                            return CartItemResponse.from(
                                    cartItem,
                                    optionValues
                            );
                        })
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

        CartItem cartItem =
                getMyCartItem(
                        userId,
                        cartItemId
                );

        Product product =
                cartItem.getProduct();

        validateProductIsAvailable(product);
        validateProductIsOnSale(product);

        ProductVariant variant =
                cartItem.getVariant();

        if (variant != null
                && !variant.isActive()) {
            throw new CartException(
                    "현재 판매하지 않는 상품 옵션입니다."
            );
        }

        int stockQuantity =
                getStockQuantity(
                        product,
                        variant
                );

        validateQuantity(
                request.quantity(),
                stockQuantity
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

        CartItem cartItem =
                getMyCartItem(
                        userId,
                        cartItemId
                );

        cartItemRepository.delete(cartItem);

        return getCart(userId);
    }

    @Transactional
    public CartResponse deleteCartItems(
            Long userId,
            List<Long> cartItemIds
    ) {
        validateAuthentication(userId);

        cartItemRepository.deleteAllByIdInAndUserId(
                cartItemIds,
                userId
        );

        return getCart(userId);
    }

    @Transactional
    public void clearCart(Long userId) {
        validateAuthentication(userId);

        cartItemRepository.deleteAllByUserId(
                userId
        );
    }

    private ProductVariant resolveVariant(
            Product product,
            Long variantId,
            boolean hasOptions
    ) {
        if (!hasOptions) {
            if (variantId != null) {
                throw new CartException(
                        "옵션이 없는 상품에는 상품 옵션을 선택할 수 없습니다."
                );
            }

            return null;
        }

        if (variantId == null) {
            throw new CartException(
                    "상품 옵션을 선택해주세요."
            );
        }

        ProductVariant variant =
                productVariantRepository
                        .findByIdAndProductId(
                                variantId,
                                product.getId()
                        )
                        .orElseThrow(() ->
                                new CartException(
                                        "선택한 상품 옵션을 찾을 수 없습니다."
                                )
                        );

        if (!variant.isActive()) {
            throw new CartException(
                    "현재 판매하지 않는 상품 옵션입니다."
            );
        }

        if (variant.getStockQuantity() <= 0) {
            throw new CartException(
                    "선택한 상품 옵션은 품절되었습니다."
            );
        }

        return variant;
    }

    private Optional<CartItem> findExistingCartItem(
            Long userId,
            Long productId,
            ProductVariant variant
    ) {
        if (variant == null) {
            return cartItemRepository
                    .findByUserIdAndProductIdAndVariantIsNull(
                            userId,
                            productId
                    );
        }

        return cartItemRepository
                .findByUserIdAndProductIdAndVariantId(
                        userId,
                        productId,
                        variant.getId()
                );
    }

    private boolean hasProductOptions(
            Long productId
    ) {
        return !productOptionGroupRepository
                .findAllByProductIdOrderBySortOrderAsc(
                        productId
                )
                .isEmpty();
    }

    private int getStockQuantity(
            Product product,
            ProductVariant variant
    ) {
        if (variant != null) {
            return variant.getStockQuantity();
        }

        return product.getStockQuantity();
    }

    private Map<Long, List<ProductVariantOptionValue>>
    getOptionValuesByVariantId(
            List<Long> variantIds
    ) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }

        List<ProductVariantOptionValue>
                variantOptionValues =
                productVariantOptionValueRepository
                        .findAllByVariantIdIn(
                                variantIds
                        );

        Map<Long, List<ProductVariantOptionValue>>
                result =
                new HashMap<>();

        for (ProductVariantOptionValue mapping
                : variantOptionValues) {
            result.computeIfAbsent(
                            mapping.getVariant().getId(),
                            key -> new ArrayList<>()
                    )
                    .add(mapping);
        }

        return result;
    }

    private User getAuthenticatedUser(
            Long userId
    ) {
        validateAuthentication(userId);

        return userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new AuthenticationException(
                                "사용자 정보를 찾을 수 없습니다."
                        )
                );
    }

    private void validateAuthentication(
            Long userId
    ) {
        if (userId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }
    }

    private Product getAvailableProduct(
            Long productId
    ) {
        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow(() ->
                                new CartException(
                                        "상품을 찾을 수 없습니다."
                                )
                        );

        validateProductIsAvailable(product);
        validateProductIsOnSale(product);

        return product;
    }

    private void validateProductIsAvailable(
            Product product
    ) {
        if (product.isDeleted()) {
            throw new CartException(
                    "삭제된 상품은 구매할 수 없습니다."
            );
        }

        if (product.isAdminHidden()) {
            throw new CartException(
                    "현재 판매가 중지된 상품입니다."
            );
        }
    }

    private void validateProductIsOnSale(
            Product product
    ) {
        if (product.getStatus()
                != ProductStatus.ON_SALE) {
            throw new CartException(
                    "현재 판매 중인 상품이 아닙니다."
            );
        }
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
                .orElseThrow(() ->
                        new CartException(
                                "장바구니 상품을 찾을 수 없습니다."
                        )
                );
    }

    private void validateQuantity(
            Integer quantity,
            Integer stockQuantity
    ) {
        if (quantity == null
                || quantity <= 0) {
            throw new CartException(
                    "수량은 1개 이상이어야 합니다."
            );
        }

        if (stockQuantity == null
                || stockQuantity <= 0) {
            throw new CartException(
                    "품절된 상품입니다."
            );
        }

        if (quantity > stockQuantity) {
            throw new CartException(
                    "상품 재고보다 많은 수량을 담을 수 없습니다."
            );
        }
    }
}
