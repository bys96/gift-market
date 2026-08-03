package com.giftmarket.seller.repository;

import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;
import com.giftmarket.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    List<SellerApplication> findAllByStatusOrderByCreatedAtAsc(
            SellerApplicationStatus status
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