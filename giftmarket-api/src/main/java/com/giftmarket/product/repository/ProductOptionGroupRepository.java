package com.giftmarket.product.repository;

import com.giftmarket.product.entity.ProductOptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductOptionGroupRepository
        extends JpaRepository<ProductOptionGroup, Long> {

    List<ProductOptionGroup> findAllByProductIdOrderBySortOrderAsc(
            Long productId
    );

    void deleteAllByProductId(Long productId);
}