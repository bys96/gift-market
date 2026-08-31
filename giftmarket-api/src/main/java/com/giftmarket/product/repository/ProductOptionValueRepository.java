package com.giftmarket.product.repository;

import com.giftmarket.product.entity.ProductOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;

public interface ProductOptionValueRepository
        extends JpaRepository<ProductOptionValue, Long> {

    List<ProductOptionValue> findAllByOptionGroupIdOrderBySortOrderAsc(
            Long optionGroupId
    );

    @EntityGraph(attributePaths = "optionGroup")
    List<ProductOptionValue> findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(
            Collection<Long> optionGroupIds
    );

    void deleteAllByOptionGroupId(Long optionGroupId);
}
