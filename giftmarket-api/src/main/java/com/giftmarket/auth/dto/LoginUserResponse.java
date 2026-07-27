package com.giftmarket.auth.dto;

import com.giftmarket.user.entity.User;

public record LoginUserResponse(
        Long id,
        String email,
        String name,
        String profileImageUrl,
        String provider,
        String role
) {

    public static LoginUserResponse from(User user) {
        return new LoginUserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProfileImageUrl(),
                user.getProvider().name(),
                user.getRole().name()
        );
    }
}