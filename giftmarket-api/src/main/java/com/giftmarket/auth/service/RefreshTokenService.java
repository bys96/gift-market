package com.giftmarket.auth.service;

import com.giftmarket.auth.config.JwtProperties;
import com.giftmarket.auth.entity.RefreshToken;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.auth.repository.RefreshTokenRepository;
import com.giftmarket.user.entity.User;
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

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String issue(User user) {
        String rawToken = generateToken();
        String tokenHash = hash(rawToken);

        Instant expiresAt = Instant.now().plusSeconds(
                jwtProperties.getRefreshTokenExpirationSeconds()
        );

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

    @Transactional
    public User validate(String rawToken) {
        RefreshToken refreshToken = findValidToken(rawToken);

        return refreshToken.getUser();
    }

    @Transactional
    public String rotate(String rawToken) {
        RefreshToken refreshToken = findValidToken(rawToken);

        String newRawToken = generateToken();
        String newTokenHash = hash(newRawToken);

        Instant newExpiresAt = Instant.now().plusSeconds(
                jwtProperties.getRefreshTokenExpirationSeconds()
        );

        refreshToken.rotate(
                newTokenHash,
                newExpiresAt
        );

        return newRawToken;
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
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthenticationException(
                    "Refresh Token이 없습니다."
            );
        }

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