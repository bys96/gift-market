package com.giftmarket.auth.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refresh_token_user",
                        columnNames = "user_id"
                ),
                @UniqueConstraint(
                        name = "uk_refresh_token_hash",
                        columnNames = "token_hash"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    private static final long PREVIOUS_TOKEN_GRACE_SECONDS = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_token_user")
    )
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @Column(name = "previous_token_expires_at")
    private Instant previousTokenExpiresAt;

    @Column(name = "token_value_encrypted", length = 512)
    private String tokenValueEncrypted;

    private RefreshToken(
            User user,
            String tokenHash,
            Instant expiresAt
    ) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken create(
            User user,
            String tokenHash,
            Instant expiresAt
    ) {
        return new RefreshToken(user, tokenHash, expiresAt);
    }

    public void rotate(
            String tokenHash,
            Instant expiresAt,
            String tokenValueEncrypted
    ) {
        this.previousTokenHash = this.tokenHash;
        this.previousTokenExpiresAt = Instant.now().plusSeconds(
                PREVIOUS_TOKEN_GRACE_SECONDS
        );
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.tokenValueEncrypted = tokenValueEncrypted;
    }

    public void setTokenValueEncrypted(String tokenValueEncrypted) {
        this.tokenValueEncrypted = tokenValueEncrypted;
    }

    public String getTokenValueEncrypted() {
        return tokenValueEncrypted;
    }

    public boolean matchesCurrentToken(String tokenHash) {
        return this.tokenHash.equals(tokenHash);
    }

    public boolean matchesPreviousToken(String tokenHash) {
        return previousTokenHash != null
                && previousTokenHash.equals(tokenHash)
                && previousTokenExpiresAt != null
                && previousTokenExpiresAt.isAfter(Instant.now());
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
