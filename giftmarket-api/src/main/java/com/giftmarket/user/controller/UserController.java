package com.giftmarket.user.controller;

import com.giftmarket.auth.dto.LoginUserResponse;
import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.user.dto.UpdateMyProfileRequest;
import com.giftmarket.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/me")
    public ApiResponse<LoginUserResponse> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        return ApiResponse.success(
                userService.updateMyProfile(userId, request)
        );
    }
}