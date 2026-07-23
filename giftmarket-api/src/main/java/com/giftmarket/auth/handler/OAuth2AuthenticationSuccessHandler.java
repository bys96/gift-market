package com.giftmarket.auth.handler;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.auth.service.RefreshTokenService;
import com.giftmarket.auth.util.RefreshTokenCookieManager;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler
        implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieManager refreshTokenCookieManager;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new AuthenticationException(
                    "Google 사용자 정보를 확인할 수 없습니다."
            );
        }

        User user = userRepository
                .findByProviderAndProviderId(
                        AuthProvider.GOOGLE,
                        oidcUser.getSubject()
                )
                .orElseThrow(() -> new AuthenticationException(
                        "로그인 사용자를 찾을 수 없습니다."
                ));

        String refreshToken = refreshTokenService.issue(user);

        refreshTokenCookieManager.addRefreshTokenCookie(
                response,
                refreshToken
        );

        clearSession(request);

        log.info(
                "OAuth2 로그인 성공. userId={}, email={}",
                user.getId(),
                user.getEmail()
        );

        response.sendRedirect(
                frontendUrl + "/oauth/callback"
        );
    }

    private void clearSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }
    }
}