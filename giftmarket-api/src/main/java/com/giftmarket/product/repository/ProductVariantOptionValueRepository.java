package com.giftmarket.product.repository;

import com.giftmarket.product.entity.ProductVariantOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductVariantOptionValueRepository
        extends JpaRepository<ProductVariantOptionValue, Long> {

    List<ProductVariantOptionValue> findAllByVariantId(
            Long variantId
    );

    List<ProductVariantOptionValue> findAllByVariantIdIn(
            Collection<Long> variantIds
    );

    List<ProductVariantOptionValue> findAllByOptionValueIdIn(
            Collection<Long> optionValueIds
    );

    boolean existsByOptionValueId(
            Long optionValueId
    );

    void deleteAllByVariantId(Long variantId);

    void deleteAllByVariantProductId(Long productId);
}
