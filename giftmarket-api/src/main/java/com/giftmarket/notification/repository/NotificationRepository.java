package com.giftmarket.notification.repository;

import com.giftmarket.notification.entity.Notification;
import com.giftmarket.notification.entity.NotificationContext;
import com.giftmarket.notification.entity.NotificationReferenceType;
import com.giftmarket.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUserIdAndContext(
            Long userId,
            NotificationContext context,
            Pageable pageable
    );

    long countByUserIdAndContextAndReadAtIsNull(
            Long userId,
            NotificationContext context
    );

    Optional<Notification> findByIdAndUserIdAndContext(
            Long notificationId,
            Long userId,
            NotificationContext context
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.readAt = :readAt,
                   n.updatedAt = :readAt
             where n.user.id = :userId
               and n.context = :context
               and n.readAt is null
            """)
    int markAllAsRead(
            @Param("userId") Long userId,
            @Param("context") NotificationContext context,
            @Param("readAt") LocalDateTime readAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.readAt = :readAt,
                   n.updatedAt = :readAt
             where n.context = :context
               and n.type = :type
               and n.referenceType = :referenceType
               and n.referenceId = :referenceId
               and n.readAt is null
            """)
    int markAllByReferenceAsRead(
            @Param("context") NotificationContext context,
            @Param("type") NotificationType type,
            @Param("referenceType") NotificationReferenceType referenceType,
            @Param("referenceId") Long referenceId,
            @Param("readAt") LocalDateTime readAt
    );
}
