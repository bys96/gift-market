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
import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final JwtTokenProvider jwtTokenProvider;

    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    void validateEncryptionKey() {
        encryptionKey();
    }

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
                                expiresAt,
                                encrypt(rawToken)
                        ),
                        () -> {
                            RefreshToken refreshToken = RefreshToken.create(
                                    user,
                                    tokenHash,
                                    expiresAt
                            );
                            refreshToken.setTokenValueEncrypted(encrypt(rawToken));
                            refreshTokenRepository.save(refreshToken);
                        }
                );

        return rawToken;
    }

    /**
     * Refresh Token 검증, 회전, Access Token 발급을
     * 하나의 트랜잭션에서 처리한다.
     */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public TokenReissueResult reissue(String rawToken) {
        validateRawToken(rawToken);

        String tokenHash = hash(rawToken);
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHashOrPreviousTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new AuthenticationException(
                        "유효하지 않은 Refresh Token입니다."
                ));

        boolean currentToken = refreshToken.matchesCurrentToken(tokenHash);
        if (!currentToken && !refreshToken.matchesPreviousToken(tokenHash)) {
            throw new AuthenticationException(
                    "유효하지 않은 Refresh Token입니다."
            );
        }

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new AuthenticationException(
                    "Refresh Token이 만료되었습니다."
            );
        }

        /*
         * User가 LAZY 프록시여도 현재 트랜잭션 안에서
         * Access Token을 생성하므로 정상적으로 조회된다.
         */
        User user = refreshToken.getUser();

        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenRepository.delete(refreshToken);
            throw new AuthenticationException("이용할 수 없는 회원 계정입니다.");
        }

        // 직전 토큰을 사용한 동시 요청은 이미 회전된 현재 토큰을
        // 다시 덮어쓰지 않고 Access Token만 발급한다.
        if (!currentToken) {
            String currentRawToken = decrypt(refreshToken.getTokenValueEncrypted());
            return new TokenReissueResult(
                    jwtTokenProvider.createAccessToken(user),
                    currentRawToken
            );
        }

        String newRawToken = generateToken();
        String newTokenHash = hash(newRawToken);

        refreshToken.rotate(
                newTokenHash,
                calculateExpiration(),
                encrypt(newRawToken)
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

    private String encrypt(String rawToken) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    encryptionKey(),
                    new GCMParameterSpec(128, iv)
            );
            byte[] encrypted = cipher.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Refresh Token 암호화에 실패했습니다.", exception);
        }
    }

    private String decrypt(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.isBlank()) {
            return null;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encryptedToken);
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    encryptionKey(),
                    new GCMParameterSpec(128, iv)
            );
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new AuthenticationException("Refresh Token을 확인할 수 없습니다.");
        }
    }

    private SecretKeySpec encryptionKey() {
        try {
            String configuredKey = jwtProperties.getRefreshTokenEncryptionKey();
            if (configuredKey == null || configuredKey.isBlank()) {
                throw new IllegalStateException(
                        "REFRESH_TOKEN_ENCRYPTION_KEY가 설정되지 않았습니다."
                );
            }
            byte[] key = Base64.getDecoder().decode(configuredKey);
            if (key.length < 32) {
                throw new IllegalArgumentException("최소 32바이트가 필요합니다.");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Refresh Token 암호화 키를 준비할 수 없습니다.", exception);
        }
    }
}
