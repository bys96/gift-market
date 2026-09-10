package com.giftmarket.user.controller;

import com.giftmarket.auth.dto.LoginUserResponse;
import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.auth.util.RefreshTokenCookieManager;
import jakarta.servlet.http.HttpServletResponse;
import com.giftmarket.user.dto.UpdateMyProfileRequest;
import com.giftmarket.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RefreshTokenCookieManager refreshTokenCookieManager;

    @PatchMapping("/me")
    public ApiResponse<LoginUserResponse> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        return ApiResponse.success(
                userService.updateMyProfile(userId, request)
        );
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal Long userId,
            HttpServletResponse response
    ) {
        userService.withdraw(userId);
        refreshTokenCookieManager.deleteRefreshTokenCookie(response);
        return ApiResponse.success(null);
    }
}
