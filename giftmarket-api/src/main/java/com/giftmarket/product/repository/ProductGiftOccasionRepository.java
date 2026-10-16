package com.giftmarket.product.repository;

import com.giftmarket.product.entity.ProductGiftOccasion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductGiftOccasionRepository extends JpaRepository<ProductGiftOccasion, Long> {

    @Query("""
            select pgo from ProductGiftOccasion pgo
            join fetch pgo.giftOccasion go
            where pgo.product.id = :productId and go.active = true
            order by go.displayOrder asc, go.id asc
            """)
    List<ProductGiftOccasion> findActiveByProductId(@Param("productId") Long productId);

    void deleteAllByProductId(Long productId);
}
