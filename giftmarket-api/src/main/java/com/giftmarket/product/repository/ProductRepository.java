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
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends
        JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {

    long countByStatusAndDeletedAtIsNull(ProductStatus status);

    long countBySellerIdAndDeletedAtIsNull(Long sellerId);

    @Query("""
            select p.seller.id as sellerId, count(p.id) as productCount
            from Product p
            where p.seller.id in :sellerIds
              and p.status = :status
              and p.deletedAt is null
            group by p.seller.id
            """)
    List<SellerProductCountProjection> countBySellerIdsAndStatus(
            @Param("sellerIds") Collection<Long> sellerIds,
            @Param("status") ProductStatus status
    );

    @EntityGraph(attributePaths = {"seller", "category"})
    @Query(
            value = """
                    select p
                    from Product p
                    join p.seller s
                    where (:keyword is null
                           or lower(p.name) like lower(concat('%', :keyword, '%'))
                           or lower(s.storeName) like lower(concat('%', :keyword, '%')))
                      and (:status is null or p.status = :status)
                      and (:sellerId is null or s.id = :sellerId)
                      and (:categoryId is null or p.category.id = :categoryId)
                      and (:deleted is null
                           or (:deleted = true and p.deletedAt is not null)
                           or (:deleted = false and p.deletedAt is null))
                    """,
            countQuery = """
                    select count(p.id)
                    from Product p
                    join p.seller s
                    where (:keyword is null
                           or lower(p.name) like lower(concat('%', :keyword, '%'))
                           or lower(s.storeName) like lower(concat('%', :keyword, '%')))
                      and (:status is null or p.status = :status)
                      and (:sellerId is null or s.id = :sellerId)
                      and (:categoryId is null or p.category.id = :categoryId)
                      and (:deleted is null
                           or (:deleted = true and p.deletedAt is not null)
                           or (:deleted = false and p.deletedAt is null))
                    """
    )
    Page<Product> findAdminProducts(
            @Param("keyword") String keyword,
            @Param("status") ProductStatus status,
            @Param("sellerId") Long sellerId,
            @Param("categoryId") Long categoryId,
            @Param("deleted") Boolean deleted,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"seller", "seller.user", "category", "category.parent"})
    @Query("select p from Product p where p.id = :productId")
    Optional<Product> findAdminById(@Param("productId") Long productId);

    long countBySellerIdAndStatusAndDeletedAtIsNull(
            Long sellerId,
            ProductStatus status
    );

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
