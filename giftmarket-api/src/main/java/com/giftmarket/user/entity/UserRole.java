package com.giftmarket.user.entity;

public enum UserRole {
    USER,
    SELLER,
    ADMIN,
    SUPER_ADMIN;

    public boolean isAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }
}
