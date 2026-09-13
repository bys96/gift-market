package com.giftmarket.notification.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.notification.dto.response.NotificationPageResponse;
import com.giftmarket.notification.dto.response.NotificationUnreadCountResponse;
import com.giftmarket.notification.entity.NotificationContext;
import com.giftmarket.notification.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<NotificationPageResponse> getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(notificationService.getNotifications(
                userId,
                NotificationContext.ADMIN,
                page,
                size
        ));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(notificationService.getUnreadCount(
                userId,
                NotificationContext.ADMIN
        ));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.markAsRead(
                userId,
                NotificationContext.ADMIN,
                notificationId
        );
        return ApiResponse.success(null);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(
            @AuthenticationPrincipal Long userId
    ) {
        notificationService.markAllAsRead(userId, NotificationContext.ADMIN);
        return ApiResponse.success(null);
    }
}
