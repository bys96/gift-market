package com.giftmarket.product.repository;

import com.giftmarket.product.entity.ProductOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;

public interface ProductOptionValueRepository
        extends JpaRepository<ProductOptionValue, Long> {

    @org.springframework.data.jpa.repository.Query("select v from ProductOptionValue v where v.optionGroup.id = :optionGroupId and v.archived = false order by v.sortOrder")
    List<ProductOptionValue> findAllByOptionGroupIdOrderBySortOrderAsc(
            Long optionGroupId
    );

    @org.springframework.data.jpa.repository.Query("select v from ProductOptionValue v where v.optionGroup.id in :optionGroupIds and v.archived = false order by v.optionGroup.id, v.sortOrder")
    @EntityGraph(attributePaths = "optionGroup")
    List<ProductOptionValue> findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(
            Collection<Long> optionGroupIds
    );

    @org.springframework.data.jpa.repository.Query("select v from ProductOptionValue v where v.optionGroup.id in :optionGroupIds order by v.optionGroup.id, v.sortOrder")
    List<ProductOptionValue> findIncludingArchivedByOptionGroupIdIn(
            @org.springframework.data.repository.query.Param("optionGroupIds") Collection<Long> optionGroupIds);

    void deleteAllByOptionGroupId(Long optionGroupId);
}
