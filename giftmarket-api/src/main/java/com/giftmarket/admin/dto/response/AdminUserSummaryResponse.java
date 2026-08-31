package com.giftmarket.admin.dto.response;

import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.entity.UserStatus;

import java.time.LocalDateTime;

public record AdminUserSummaryResponse(
        Long id,
        String email,
        String name,
        UserRole role,
        AuthProvider provider,
        UserStatus status,
        LocalDateTime createdAt,
        boolean activeSeller
) {
    public static AdminUserSummaryResponse from(User user, boolean activeSeller) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getProvider(),
                user.getStatus(),
                user.getCreatedAt(),
                activeSeller
        );
    }
}
