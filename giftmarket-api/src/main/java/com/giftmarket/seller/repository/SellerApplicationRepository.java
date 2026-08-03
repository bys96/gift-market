package com.giftmarket.seller.repository;

import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;
import com.giftmarket.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
}