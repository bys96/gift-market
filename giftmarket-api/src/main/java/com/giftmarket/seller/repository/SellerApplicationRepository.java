package com.giftmarket.seller.repository;

import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;
import com.giftmarket.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SellerApplicationRepository
        extends JpaRepository<SellerApplication, Long> {

    boolean existsByUserAndStatus(
            User user,
            SellerApplicationStatus status
    );

    Optional<SellerApplication> findFirstByUserOrderByCreatedAtDesc(
            User user
    );

    @EntityGraph(attributePaths = "user")
    Page<SellerApplication> findAllByStatus(
            SellerApplicationStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select application
            from SellerApplication application
            where application.id = :applicationId
            """)
    Optional<SellerApplication> findByIdForUpdate(
            @Param("applicationId") Long applicationId
    );
}
