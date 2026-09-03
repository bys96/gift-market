package com.giftmarket.auth.util;

import com.giftmarket.auth.config.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookieManager {

    private final JwtProperties jwtProperties;

    public void addRefreshTokenCookie(
            HttpServletResponse response,
            String refreshToken
    ) {
        ResponseCookie cookie = ResponseCookie
                .from(
                        jwtProperties.getRefreshCookieName(),
                        refreshToken
                )
                .httpOnly(true)
                .secure(jwtProperties.isSecureCookie())
                .sameSite(jwtProperties.getRefreshCookieSameSite())
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(
                        jwtProperties.getRefreshTokenExpirationSeconds()
                ))
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
    }

    public void deleteRefreshTokenCookie(
            HttpServletResponse response
    ) {
        ResponseCookie cookie = ResponseCookie
                .from(
                        jwtProperties.getRefreshCookieName(),
                        ""
                )
                .httpOnly(true)
                .secure(jwtProperties.isSecureCookie())
                .sameSite(jwtProperties.getRefreshCookieSameSite())
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
    }
}