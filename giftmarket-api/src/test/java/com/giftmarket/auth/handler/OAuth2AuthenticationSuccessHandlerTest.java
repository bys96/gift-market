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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenService refreshTokenService;
    @Mock RefreshTokenCookieManager cookieManager;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock OidcUser oidcUser;
    @Mock User user;

    private OAuth2AuthenticationSuccessHandler handler;
    private OAuth2AuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        handler = new OAuth2AuthenticationSuccessHandler(
                userRepository,
                refreshTokenService,
                cookieManager
        );
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://frontend.test");
        authentication = new OAuth2AuthenticationToken(oidcUser, List.of(), "google");
        given(oidcUser.getSubject()).willReturn("google-subject");
        given(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-subject"))
                .willReturn(Optional.of(user));
    }

    @Test
    void activeUserKeepsExistingSuccessFlow() throws Exception {
        given(user.getStatus()).willReturn(UserStatus.ACTIVE);
        given(refreshTokenService.issue(user)).willReturn("refresh-token");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(cookieManager).addRefreshTokenCookie(response, "refresh-token");
        verify(response).sendRedirect("http://frontend.test/oauth/callback");
    }

    @Test
    void suspendedUserCannotReceiveRefreshTokenOrSuccessRedirect() {
        assertInactiveUserRejected(UserStatus.SUSPENDED);
    }

    @Test
    void withdrawnUserCannotReceiveRefreshTokenOrSuccessRedirect() {
        assertInactiveUserRejected(UserStatus.WITHDRAWN);
    }

    private void assertInactiveUserRejected(UserStatus status) {
        given(user.getStatus()).willReturn(status);

        assertThatThrownBy(() -> handler.onAuthenticationSuccess(request, response, authentication))
                .isInstanceOf(AuthenticationException.class);

        verify(refreshTokenService, never()).issue(user);
        verify(cookieManager, never()).addRefreshTokenCookie(response, "refresh-token");
        try {
            verify(response, never()).sendRedirect("http://frontend.test/oauth/callback");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
