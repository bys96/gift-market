package com.giftmarket.product.repository;

import com.giftmarket.product.entity.ProductOptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductOptionGroupRepository
        extends JpaRepository<ProductOptionGroup, Long> {

    List<ProductOptionGroup> findAllByProductIdOrderBySortOrderAsc(
            Long productId
    );

    @Query("select distinct g.product.id from ProductOptionGroup g where g.product.id in :productIds")
    List<Long> findProductIdsWithOptions(@Param("productIds") Collection<Long> productIds);

    void deleteAllByProductId(Long productId);
}
