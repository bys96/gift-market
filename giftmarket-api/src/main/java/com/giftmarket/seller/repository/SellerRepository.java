package com.giftmarket.seller.repository;

import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface SellerRepository extends JpaRepository<Seller, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seller s where s.id = :sellerId")
    Optional<Seller> findByIdForUpdate(@Param("sellerId") Long sellerId);

    long countByStatus(SellerStatus status);

    @EntityGraph(attributePaths = "user")
    @org.springframework.data.jpa.repository.Query(
            value = """
                    select s
                    from Seller s
                    join s.user u
                    where (:keyword is null
                           or lower(s.storeName) like lower(concat('%', :keyword, '%'))
                           or lower(u.name) like lower(concat('%', :keyword, '%'))
                           or lower(u.email) like lower(concat('%', :keyword, '%')))
                      and (:status is null or s.status = :status)
                    """,
            countQuery = """
                    select count(s.id)
                    from Seller s
                    join s.user u
                    where (:keyword is null
                           or lower(s.storeName) like lower(concat('%', :keyword, '%'))
                           or lower(u.name) like lower(concat('%', :keyword, '%'))
                           or lower(u.email) like lower(concat('%', :keyword, '%')))
                      and (:status is null or s.status = :status)
                    """
    )
    Page<Seller> findAdminSellers(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("status") SellerStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "user")
    Optional<Seller> findWithUserById(Long sellerId);

    boolean existsByUser(User user);

    Optional<Seller> findByUser(User user);

    Optional<Seller> findByUserId(Long userId);

    @org.springframework.data.jpa.repository.Query("""
            select s.user.id
            from Seller s
            where s.user.id in :userIds
              and s.status = :status
            """)
    List<Long> findUserIdsByUserIdInAndStatus(
            @org.springframework.data.repository.query.Param("userIds") Collection<Long> userIds,
            @org.springframework.data.repository.query.Param("status") SellerStatus status
    );
}
