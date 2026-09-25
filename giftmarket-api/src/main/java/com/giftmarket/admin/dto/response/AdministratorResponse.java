package com.giftmarket.admin.dto.response;

import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;

import java.time.LocalDateTime;

public record AdministratorResponse(
        Long id,
        String name,
        String email,
        UserRole role,
        LocalDateTime createdAt,
        boolean seller,
        LocalDateTime administratorAssignedAt
) {
    public static AdministratorResponse from(
            User user,
            boolean seller,
            LocalDateTime administratorAssignedAt
    ) {
        return new AdministratorResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                seller,
                administratorAssignedAt
        );
    }
}
