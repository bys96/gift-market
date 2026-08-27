package com.giftmarket.review.repository;

import com.giftmarket.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    @EntityGraph(attributePaths = {"user", "product", "variant", "orderItem"})
    Optional<Review> findByOrderItemId(Long orderItemId);

    @EntityGraph(attributePaths = {"user", "product", "variant", "orderItem"})
    Optional<Review> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"user"})
    Page<Review> findAllByProductIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    @Query("select r.orderItem.id, r.id from Review r where r.orderItem.id in :ids and r.deletedAt is null")
    List<Object[]> findActiveIdsByOrderItemIds(@Param("ids") Collection<Long> ids);

    @Query("select count(r) as reviewCount, coalesce(avg(r.rating), 0.0) as averageRating from Review r where r.product.id = :productId and r.deletedAt is null")
    ReviewSummaryProjection summarize(@Param("productId") Long productId);
}
