package com.giftmarket.inquiry.repository;

import com.giftmarket.inquiry.entity.ProductInquiry;
import com.giftmarket.inquiry.entity.ProductInquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface ProductInquiryRepository extends JpaRepository<ProductInquiry, Long> {
    long countByUserIdAndDeletedAtIsNull(Long userId);
    @EntityGraph(attributePaths = {"product", "product.seller", "product.seller.user", "user"})
    Page<ProductInquiry> findAllByProductIdAndDeletedAtIsNull(Long productId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "product.seller", "product.seller.user", "user"})
    Optional<ProductInquiry> findByIdAndProductIdAndDeletedAtIsNull(Long id, Long productId);

    @EntityGraph(attributePaths = {"product", "product.seller", "product.seller.user", "user"})
    Optional<ProductInquiry> findByIdAndProductId(Long id, Long productId);

    @EntityGraph(attributePaths = {"product", "product.seller", "product.seller.user", "user"})
    Page<ProductInquiry> findAllByProductSellerIdAndDeletedAtIsNull(Long sellerId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "product.seller", "product.seller.user", "user"})
    Page<ProductInquiry> findAllByProductSellerIdAndStatusAndDeletedAtIsNull(Long sellerId, ProductInquiryStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "product.seller", "product.seller.user", "user"})
    Optional<ProductInquiry> findByIdAndProductSellerIdAndDeletedAtIsNull(Long id, Long sellerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from ProductInquiry i
            join fetch i.product p
            join fetch p.seller s
            join fetch s.user
            join fetch i.user
            where i.id = :id and s.id = :sellerId and i.deletedAt is null
            """)
    Optional<ProductInquiry> findActiveByIdAndSellerIdForUpdate(@Param("id") Long id, @Param("sellerId") Long sellerId);
}
