package com.giftmarket.auth.repository;

import com.giftmarket.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token from RefreshToken token
            where token.tokenHash = :tokenHash
               or token.previousTokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHashOrPreviousTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    Optional<RefreshToken> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
