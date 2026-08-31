package com.giftmarket.product.repository;

import com.giftmarket.product.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository
        extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findAllByProductIdOrderByIdAsc(
            Long productId
    );

    List<ProductVariant> findAllByProductIdAndActiveTrueOrderByIdAsc(
            Long productId
    );

    @Query("""
            select v.product.id as productId, coalesce(sum(v.stockQuantity), 0) as stockQuantity
            from ProductVariant v
            where v.product.id in :productIds
              and v.active = true
            group by v.product.id
            """)
    List<ProductStockProjection> sumActiveStockByProductIds(
            @Param("productIds") Collection<Long> productIds
    );

    Optional<ProductVariant> findBySkuCode(
            String skuCode
    );

    Optional<ProductVariant> findByIdAndProductId(
            Long variantId,
            Long productId
    );

    Optional<ProductVariant> findByProductIdAndSkuCode(
            Long productId,
            String skuCode
    );

    Optional<ProductVariant> findByProductIdAndCombinationKey(
            Long productId,
            String combinationKey
    );

    void deleteAllByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductVariant> findWithLockByIdAndProductId(
            Long variantId,
            Long productId
    );
}
