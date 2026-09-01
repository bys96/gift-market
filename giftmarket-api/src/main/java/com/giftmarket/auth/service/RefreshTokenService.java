package com.giftmarket.auth.service;

import com.giftmarket.auth.config.JwtProperties;
import com.giftmarket.auth.dto.TokenReissueResult;
import com.giftmarket.auth.entity.RefreshToken;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.auth.jwt.JwtTokenProvider;
import com.giftmarket.auth.repository.RefreshTokenRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final JwtTokenProvider jwtTokenProvider;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 로그인 성공 시 Refresh Token을 최초 발급하거나 기존 토큰을 교체한다.
     */
    @Transactional
    public String issue(User user) {
        String rawToken = generateToken();
        String tokenHash = hash(rawToken);

        Instant expiresAt = calculateExpiration();

        refreshTokenRepository.findByUserId(user.getId())
                .ifPresentOrElse(
                        refreshToken -> refreshToken.rotate(
                                tokenHash,
                                expiresAt
                        ),
                        () -> refreshTokenRepository.save(
                                RefreshToken.create(
                                        user,
                                        tokenHash,
                                        expiresAt
                                )
                        )
                );

        return rawToken;
    }

    /**
     * Refresh Token 검증, 회전, Access Token 발급을
     * 하나의 트랜잭션에서 처리한다.
     */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public TokenReissueResult reissue(String rawToken) {
        RefreshToken refreshToken = findValidToken(rawToken);

        /*
         * User가 LAZY 프록시여도 현재 트랜잭션 안에서
         * Access Token을 생성하므로 정상적으로 조회된다.
         */
        User user = refreshToken.getUser();

        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenRepository.delete(refreshToken);
            throw new AuthenticationException("이용할 수 없는 회원 계정입니다.");
        }

        String newRawToken = generateToken();
        String newTokenHash = hash(newRawToken);

        refreshToken.rotate(
                newTokenHash,
                calculateExpiration()
        );

        String accessToken = jwtTokenProvider.createAccessToken(user);

        return new TokenReissueResult(
                accessToken,
                newRawToken
        );
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository
                .findByTokenHash(hash(rawToken))
                .ifPresent(refreshTokenRepository::delete);
    }

    private RefreshToken findValidToken(String rawToken) {
        validateRawToken(rawToken);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AuthenticationException(
                        "유효하지 않은 Refresh Token입니다."
                ));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);

            throw new AuthenticationException(
                    "Refresh Token이 만료되었습니다."
            );
        }

        return refreshToken;
    }

    private void validateRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthenticationException(
                    "Refresh Token이 없습니다."
            );
        }
    }

    private Instant calculateExpiration() {
        return Instant.now().plusSeconds(
                jwtProperties.getRefreshTokenExpirationSeconds()
        );
    }

    private String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];

        secureRandom.nextBytes(tokenBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = messageDigest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 알고리즘을 사용할 수 없습니다.",
                    exception
            );
        }
    }
}
