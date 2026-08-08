package com.giftmarket.cart.repository;

import com.giftmarket.cart.entity.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = {
            "product",
            "product.seller",
            "product.category"
    })
    List<CartItem> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<CartItem> findByUserIdAndProductId(
            Long userId,
            Long productId
    );

    Optional<CartItem> findByIdAndUserId(
            Long cartItemId,
            Long userId
    );

    void deleteAllByUserId(Long userId);
}