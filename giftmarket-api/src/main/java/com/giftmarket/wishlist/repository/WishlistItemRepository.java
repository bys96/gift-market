package com.giftmarket.wishlist.repository;

import com.giftmarket.wishlist.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("""
            select wi
            from WishlistItem wi
            join fetch wi.product p
            join fetch p.category
            where wi.user.id = :userId
              and p.deletedAt is null
            order by wi.createdAt desc, wi.id desc
            """)
    List<WishlistItem> findVisibleByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    long deleteByUserIdAndProductId(Long userId, Long productId);

    @Query("""
            select count(wi)
            from WishlistItem wi
            join wi.product p
            where wi.user.id = :userId
              and p.deletedAt is null
            """)
    long countVisibleByUserId(@Param("userId") Long userId);
}
