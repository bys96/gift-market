package com.giftmarket.cart.repository;

import com.giftmarket.cart.entity.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository
        extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = {
            "product",
            "product.seller",
            "product.category",
            "variant"
    })
    List<CartItem> findAllByUserIdOrderByCreatedAtDesc(
            Long userId
    );

    Optional<CartItem>
    findByUserIdAndProductIdAndVariantIsNull(
            Long userId,
            Long productId
    );

    Optional<CartItem>
    findByUserIdAndProductIdAndVariantId(
            Long userId,
            Long productId,
            Long variantId
    );

    @EntityGraph(attributePaths = {
            "product",
            "product.seller",
            "variant"
    })
    Optional<CartItem> findByIdAndUserId(
            Long cartItemId,
            Long userId
    );

    void deleteAllByUserId(Long userId);

    void deleteAllByIdInAndUserId(
            List<Long> cartItemIds,
            Long userId
    );

    @EntityGraph(attributePaths = {
            "product",
            "product.seller",
            "variant"
    })
    List<CartItem> findAllByIdInAndUserId(
            List<Long> cartItemIds,
            Long userId
    );
}