package com.giftmarket.product.repository;

import com.giftmarket.product.entity.ProductOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductOptionValueRepository
        extends JpaRepository<ProductOptionValue, Long> {

    List<ProductOptionValue> findAllByOptionGroupIdOrderBySortOrderAsc(
            Long optionGroupId
    );

    void deleteAllByOptionGroupId(Long optionGroupId);
}