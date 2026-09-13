package com.giftmarket.notification.dto.response;

import com.giftmarket.notification.entity.Notification;
import com.giftmarket.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        String targetUrl,
        boolean read,
        LocalDateTime createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getTargetUrl(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
