package com.giftmarket.product.repository;

import com.giftmarket.product.entity.ProductOptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductOptionGroupRepository
        extends JpaRepository<ProductOptionGroup, Long> {

    @Query("select g from ProductOptionGroup g where g.product.id = :productId and g.archived = false order by g.sortOrder")
    List<ProductOptionGroup> findAllByProductIdOrderBySortOrderAsc(
            Long productId
    );

    @Query("select g from ProductOptionGroup g where g.product.id = :productId order by g.sortOrder")
    List<ProductOptionGroup> findIncludingArchivedByProductId(@Param("productId") Long productId);

    @Query("select p.id from Product p where p.id in :productIds and (exists (select g.id from ProductOptionGroup g where g.product.id = p.id and g.archived = false) or exists (select v.id from ProductVariant v where v.product.id = p.id))")
    List<Long> findProductIdsWithOptions(@Param("productIds") Collection<Long> productIds);

    void deleteAllByProductId(Long productId);
}
