package com.giftmarket.product.repository;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends
        JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndSellerIdAndDeletedAtIsNull(
            Long productId,
            Long sellerId
    );

    Optional<Product> findByIdAndStatusInAndDeletedAtIsNull(
            Long productId,
            Collection<ProductStatus> statuses
    );

    Page<Product> findAllBySellerIdAndDeletedAtIsNull(
            Long sellerId,
            Pageable pageable
    );

    Page<Product> findAllBySellerIdAndStatusAndDeletedAtIsNull(
            Long sellerId,
            ProductStatus status,
            Pageable pageable
    );

    Page<Product> findAllByStatusAndDeletedAtIsNull(
            ProductStatus status,
            Pageable pageable
    );

    Page<Product> findByStatusInAndDeletedAtIsNull(
            List<ProductStatus> statuses,
            Pageable pageable
    );

    Page<Product> findByStatusInAndCategory_IdAndDeletedAtIsNull(
            List<ProductStatus> statuses,
            Long categoryId,
            Pageable pageable
    );

    Page<Product> findByStatusInAndNameContainingIgnoreCaseAndDeletedAtIsNull(
            List<ProductStatus> statuses,
            String keyword,
            Pageable pageable
    );

    Page<Product> findByStatusInAndCategory_IdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
            List<ProductStatus> statuses,
            Long categoryId,
            String keyword,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findWithLockByIdAndDeletedAtIsNull(
            Long productId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p
        from Product p
        where p.id = :productId
        """)
    Optional<Product> findByIdForUpdate(
            @Param("productId") Long productId
    );
}