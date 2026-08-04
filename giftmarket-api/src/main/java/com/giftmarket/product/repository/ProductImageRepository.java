package com.giftmarket.product.repository;

import com.giftmarket.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository
        extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findAllByProductIdOrderBySortOrderAsc(
            Long productId
    );

    void deleteAllByProductId(Long productId);
}