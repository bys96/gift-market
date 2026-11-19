package com.giftmarket.auth.service;

import com.giftmarket.auth.config.JwtProperties;
import com.giftmarket.auth.entity.RefreshToken;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.auth.jwt.JwtTokenProvider;
import com.giftmarket.auth.repository.RefreshTokenRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserStatus;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock UserRepository userRepository;
    @Mock User user;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
        properties.setRefreshTokenEncryptionKey("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
        properties.setRefreshTokenExpirationSeconds(3600);
        service = new RefreshTokenService(refreshTokenRepository, properties, jwtTokenProvider, userRepository);
    }

    @Test
    void recordsLastLoginWhenIssuingRefreshToken() {
        given(user.getId()).willReturn(1L);
        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        assertThat(service.issue(user)).isNotBlank();

        ArgumentCaptor<LocalDateTime> loginAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).updateLastLoginAt(eq(1L), loginAt.capture());
        assertThat(loginAt.getValue()).isBetween(before, LocalDateTime.now());
    }

    @Test
    void reissuesTokensForActiveUser() {
        RefreshToken refreshToken = validRefreshToken();
        given(user.getStatus()).willReturn(UserStatus.ACTIVE);
        given(refreshTokenRepository.findByTokenHashOrPreviousTokenHashForUpdate(anyString()))
                .willReturn(Optional.of(refreshToken));
        given(jwtTokenProvider.createAccessToken(user)).willReturn("new-access-token");

        var result = service.reissue("valid-refresh-token");

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isNotBlank();
        verify(refreshTokenRepository, never()).delete(refreshToken);
        verifyNoInteractions(userRepository);
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
        given(refreshTokenRepository.findByTokenHashOrPreviousTokenHashForUpdate(anyString()))
                .willReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> service.reissue("valid-refresh-token"))
                .isInstanceOf(AuthenticationException.class);

        verify(refreshTokenRepository).delete(refreshToken);
        verify(jwtTokenProvider, never()).createAccessToken(user);
    }

    private RefreshToken validRefreshToken() {
        return RefreshToken.create(
                user,
                hash("valid-refresh-token"),
                Instant.now().plusSeconds(3600)
        );
    }

    private String hash(String token) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
