package com.giftmarket.auth.repository;

import com.giftmarket.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}