package com.giftmarket.seller.repository;

import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface SellerRepository extends JpaRepository<Seller, Long> {

    long countByStatus(SellerStatus status);

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
