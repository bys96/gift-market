package com.giftmarket.user.repository;

import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserStatus;
import com.giftmarket.user.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    long countByStatusNot(UserStatus status);

    @Query("""
            select u
            from User u
            where (:keyword is null
                   or lower(u.email) like lower(concat('%', :keyword, '%'))
                   or lower(u.name) like lower(concat('%', :keyword, '%')))
              and (:role is null or u.role = :role)
              and (:provider is null or u.provider = :provider)
              and (:status is null or u.status = :status)
            """)
    Page<User> findAdminUsers(
            @Param("keyword") String keyword,
            @Param("role") UserRole role,
            @Param("provider") AuthProvider provider,
            @Param("status") UserStatus status,
            Pageable pageable
    );

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
