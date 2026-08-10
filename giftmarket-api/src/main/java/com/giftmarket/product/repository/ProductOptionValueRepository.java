package com.giftmarket.product.repository;

import com.giftmarket.product.entity.ProductOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductOptionValueRepository
        extends JpaRepository<ProductOptionValue, Long> {

    List<ProductOptionValue> findAllByOptionGroupIdOrderBySortOrderAsc(
            Long optionGroupId
    );

    List<ProductOptionValue> findAllByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAsc(
            Collection<Long> optionGroupIds
    );

    void deleteAllByOptionGroupId(Long optionGroupId);
}