package com.giftmarket.auth.jwt;

import com.giftmarket.auth.config.JwtProperties;
import com.giftmarket.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";
    private static final String EMAIL_CLAIM = "email";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";

    private final JwtProperties jwtProperties;

    private SecretKey secretKey;

    @PostConstruct
    void initializeSecretKey() {
        byte[] decodedSecret = Decoders.BASE64.decode(jwtProperties.getSecret());

        if (decodedSecret.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET은 Base64 디코딩 기준 최소 32바이트 이상이어야 합니다."
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(decodedSecret);
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(
                jwtProperties.getAccessTokenExpirationSeconds()
        );

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(EMAIL_CLAIM, user.getEmail())
                .claim(ROLE_CLAIM, user.getRole().name())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);

        if (!ACCESS_TOKEN_TYPE.equals(tokenType)) {
            throw new JwtException("Access Token이 아닙니다.");
        }

        return claims;
    }

    public boolean isValidAccessToken(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseAccessToken(token).getSubject());
    }

    public String getRole(String token) {
        return parseAccessToken(token).get(ROLE_CLAIM, String.class);
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.getAccessTokenExpirationSeconds();
    }
}