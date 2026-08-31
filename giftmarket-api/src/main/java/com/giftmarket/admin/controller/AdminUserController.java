package com.giftmarket.admin.controller;

import com.giftmarket.admin.dto.response.AdminUserDetailResponse;
import com.giftmarket.admin.dto.response.AdminUserPageResponse;
import com.giftmarket.admin.service.AdminUserService;
import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.entity.UserStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<AdminUserPageResponse> getUsers(
            @AuthenticationPrincipal Long adminUserId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) AuthProvider provider,
            @RequestParam(required = false) UserStatus status
    ) {
        return ApiResponse.success(adminUserService.getUsers(
                adminUserId, page, size, keyword, role, provider, status
        ));
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminUserDetailResponse> getUser(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long userId
    ) {
        return ApiResponse.success(adminUserService.getUser(adminUserId, userId));
    }
}
