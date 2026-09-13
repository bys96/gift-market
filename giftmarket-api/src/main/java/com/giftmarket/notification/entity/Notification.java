package com.giftmarket.notification.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(
                        name = "idx_notifications_user_context_created_at",
                        columnList = "user_id, context, created_at"
                ),
                @Index(
                        name = "idx_notifications_user_context_read_at",
                        columnList = "user_id, context, read_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_TARGET_URL_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_notifications_user")
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationContext context;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, length = MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "target_url", length = MAX_TARGET_URL_LENGTH)
    private String targetUrl;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    private Notification(
            User user,
            NotificationContext context,
            NotificationType type,
            String title,
            String message,
            String targetUrl
    ) {
        if (user == null) {
            throw new IllegalArgumentException("알림 수신자가 필요합니다.");
        }
        if (context == null) {
            throw new IllegalArgumentException("알림 context가 필요합니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("알림 type이 필요합니다.");
        }
        if (type.getContext() != context) {
            throw new IllegalArgumentException("알림 context와 type이 일치하지 않습니다.");
        }

        this.user = user;
        this.context = context;
        this.type = type;
        this.title = normalizeRequired(title, MAX_TITLE_LENGTH, "알림 제목");
        this.message = normalizeRequired(message, MAX_MESSAGE_LENGTH, "알림 내용");
        this.targetUrl = normalizeOptional(targetUrl, MAX_TARGET_URL_LENGTH);
    }

    public static Notification create(
            User user,
            NotificationContext context,
            NotificationType type,
            String title,
            String message,
            String targetUrl
    ) {
        return new Notification(
                user,
                context,
                type,
                title,
                message,
                targetUrl
        );
    }

    public void markAsRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    private static String normalizeRequired(
            String value,
            int maxLength,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "이 필요합니다.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "은 " + maxLength + "자 이하여야 합니다."
            );
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    "알림 target URL은 " + maxLength + "자 이하여야 합니다."
            );
        }
        return normalized;
    }
}
