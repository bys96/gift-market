package com.giftmarket.product.repository;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndSellerId(
            Long productId,
            Long sellerId
    );

    Page<Product> findAllBySellerId(
            Long sellerId,
            Pageable pageable
    );

    Page<Product> findAllBySellerIdAndStatus(
            Long sellerId,
            ProductStatus status,
            Pageable pageable
    );
}