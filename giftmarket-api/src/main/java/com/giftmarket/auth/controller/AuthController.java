package com.giftmarket.auth.controller;

import com.giftmarket.auth.dto.LoginUserResponse;
import com.giftmarket.auth.dto.TokenResponse;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.auth.jwt.JwtTokenProvider;
import com.giftmarket.auth.service.RefreshTokenService;
import com.giftmarket.auth.util.RefreshTokenCookieManager;
import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieManager refreshTokenCookieManager;
    private final UserRepository userRepository;

    @PostMapping("/token")
    public ApiResponse<TokenResponse> reissueAccessToken(
            @CookieValue(
                    name = "${app.jwt.refresh-cookie-name}",
                    required = false
            )
            String refreshToken,
            HttpServletResponse response
    ) {
        User user = refreshTokenService.validate(refreshToken);

        String newRefreshToken = refreshTokenService.rotate(refreshToken);

        refreshTokenCookieManager.addRefreshTokenCookie(
                response,
                newRefreshToken
        );

        String accessToken = jwtTokenProvider.createAccessToken(user);

        return ApiResponse.success(
                TokenResponse.bearer(
                        accessToken,
                        jwtTokenProvider.getAccessTokenExpirationSeconds()
                )
        );
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(
                    name = "${app.jwt.refresh-cookie-name}",
                    required = false
            )
            String refreshToken,
            HttpServletResponse response
    ) {
        refreshTokenService.revoke(refreshToken);

        refreshTokenCookieManager.deleteRefreshTokenCookie(response);

        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<LoginUserResponse> getCurrentUser(
            @AuthenticationPrincipal Long userId
    ) {
        if (userId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException(
                        "사용자를 찾을 수 없습니다."
                ));

        return ApiResponse.success(
                LoginUserResponse.from(user)
        );
    }
}