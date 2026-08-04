package com.giftmarket.product.repository;

import com.giftmarket.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository
        extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findAllByProductIdOrderByIdAsc(
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
}