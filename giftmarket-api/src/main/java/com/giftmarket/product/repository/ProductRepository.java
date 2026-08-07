package com.giftmarket.product.repository;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends
        JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndSellerId(
            Long productId,
            Long sellerId
    );

    Optional<Product> findByIdAndStatusIn(
            Long productId,
            Collection<ProductStatus> statuses
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

    Page<Product> findAllByStatus(
            ProductStatus status,
            Pageable pageable
    );

    Page<Product> findByStatusIn(
            List<ProductStatus> statuses,
            Pageable pageable
    );

    Page<Product> findByStatusInAndCategory_Id(
            List<ProductStatus> statuses,
            Long categoryId,
            Pageable pageable
    );

    Page<Product> findByStatusInAndNameContainingIgnoreCase(
            List<ProductStatus> statuses,
            String keyword,
            Pageable pageable
    );

    Page<Product> findByStatusInAndCategory_IdAndNameContainingIgnoreCase(
            List<ProductStatus> statuses,
            Long categoryId,
            String keyword,
            Pageable pageable
    );
}