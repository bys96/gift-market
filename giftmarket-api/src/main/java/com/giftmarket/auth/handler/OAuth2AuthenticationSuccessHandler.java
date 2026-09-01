package com.giftmarket.auth.handler;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.auth.service.RefreshTokenService;
import com.giftmarket.auth.util.RefreshTokenCookieManager;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserStatus;
import com.giftmarket.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
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

        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            throw new AuthenticationException(
                    "OAuth2 인증 정보를 확인할 수 없습니다."
            );
        }

        String registrationId =
                oauthToken.getAuthorizedClientRegistrationId();

        AuthProvider provider = resolveProvider(registrationId);
        String providerId = resolveProviderId(provider, oauthToken.getPrincipal());

        User user = userRepository
                .findByProviderAndProviderId(provider, providerId)
                .orElseThrow(() -> new AuthenticationException(
                        "로그인 사용자를 찾을 수 없습니다."
                ));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationException("이용할 수 없는 회원 계정입니다.");
        }

        String refreshToken = refreshTokenService.issue(user);

        refreshTokenCookieManager.addRefreshTokenCookie(
                response,
                refreshToken
        );

        clearSession(request);

        log.info(
                "OAuth2 로그인 성공. provider={}, userId={}, email={}",
                provider,
                user.getId(),
                user.getEmail()
        );

        response.sendRedirect(
                frontendUrl + "/oauth/callback"
        );
    }

    private AuthProvider resolveProvider(String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> AuthProvider.GOOGLE;
            case "kakao" -> AuthProvider.KAKAO;
            default -> throw new AuthenticationException(
                    "지원하지 않는 OAuth 제공자입니다."
            );
        };
    }

    private String resolveProviderId(
            AuthProvider provider,
            OAuth2User oauth2User
    ) {
        return switch (provider) {
            case GOOGLE -> resolveGoogleProviderId(oauth2User);
            case KAKAO -> resolveKakaoProviderId(oauth2User);
            default -> throw new AuthenticationException(
                    "OAuth 사용자 정보를 확인할 수 없습니다."
            );
        };
    }

    private String resolveGoogleProviderId(OAuth2User oauth2User) {
        if (!(oauth2User instanceof OidcUser oidcUser)) {
            throw new AuthenticationException(
                    "Google 사용자 정보를 확인할 수 없습니다."
            );
        }

        String subject = oidcUser.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new AuthenticationException(
                    "Google 사용자 식별자를 확인할 수 없습니다."
            );
        }

        return subject;
    }

    private String resolveKakaoProviderId(OAuth2User oauth2User) {
        Object id = oauth2User.getAttribute("id");

        if (id == null) {
            throw new AuthenticationException(
                    "Kakao 사용자 식별자를 확인할 수 없습니다."
            );
        }

        return String.valueOf(id);
    }

    private void clearSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }
    }
}
