package com.giftmarket.user.repository;

import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(
            AuthProvider provider,
            String providerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u
            from User u
            where u.id = :userId
            """)
    Optional<User> findByIdForUpdate(
            @Param("userId") Long userId
    );
}