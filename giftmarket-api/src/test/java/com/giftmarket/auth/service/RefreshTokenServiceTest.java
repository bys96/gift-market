package com.giftmarket.auth.service;

import com.giftmarket.auth.config.JwtProperties;
import com.giftmarket.auth.entity.RefreshToken;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.auth.jwt.JwtTokenProvider;
import com.giftmarket.auth.repository.RefreshTokenRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock User user;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setRefreshTokenExpirationSeconds(3600);
        service = new RefreshTokenService(refreshTokenRepository, properties, jwtTokenProvider);
    }

    @Test
    void reissuesTokensForActiveUser() {
        RefreshToken refreshToken = validRefreshToken();
        given(user.getStatus()).willReturn(UserStatus.ACTIVE);
        given(refreshTokenRepository.findByTokenHash(anyString()))
                .willReturn(Optional.of(refreshToken));
        given(jwtTokenProvider.createAccessToken(user)).willReturn("new-access-token");

        var result = service.reissue("valid-refresh-token");

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isNotBlank();
        verify(refreshTokenRepository, never()).delete(refreshToken);
    }

    @Test
    void rejectsSuspendedUserAndDeletesRefreshToken() {
        assertInactiveUserRejected(UserStatus.SUSPENDED);
    }

    @Test
    void rejectsWithdrawnUserAndDeletesRefreshToken() {
        assertInactiveUserRejected(UserStatus.WITHDRAWN);
    }

    private void assertInactiveUserRejected(UserStatus status) {
        RefreshToken refreshToken = validRefreshToken();
        given(user.getStatus()).willReturn(status);
        given(refreshTokenRepository.findByTokenHash(anyString()))
                .willReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> service.reissue("valid-refresh-token"))
                .isInstanceOf(AuthenticationException.class);

        verify(refreshTokenRepository).delete(refreshToken);
        verify(jwtTokenProvider, never()).createAccessToken(user);
    }

    private RefreshToken validRefreshToken() {
        return RefreshToken.create(
                user,
                "a".repeat(64),
                Instant.now().plusSeconds(3600)
        );
    }
}
