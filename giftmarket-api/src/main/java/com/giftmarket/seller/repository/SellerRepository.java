package com.giftmarket.seller.repository;

import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SellerRepository extends JpaRepository<Seller, Long> {

    boolean existsByUser(User user);

    Optional<Seller> findByUser(User user);

    Optional<Seller> findByUserId(Long userId);
}