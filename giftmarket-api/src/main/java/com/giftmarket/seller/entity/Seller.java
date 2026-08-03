package com.giftmarket.seller.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "sellers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_sellers_user_id",
                        columnNames = "user_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @Column(
            name = "store_name",
            nullable = false,
            length = 100
    )
    private String storeName;

    @Column(length = 1000)
    private String introduction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SellerStatus status;

    @Column(
            name = "approved_at",
            nullable = false
    )
    private LocalDateTime approvedAt;

    @Builder
    private Seller(
            User user,
            String storeName,
            String introduction,
            SellerStatus status,
            LocalDateTime approvedAt
    ) {
        this.user = user;
        this.storeName = storeName;
        this.introduction = introduction;
        this.status = status;
        this.approvedAt = approvedAt;
    }

    public static Seller create(
            User user,
            String storeName,
            String introduction
    ) {
        return Seller.builder()
                .user(user)
                .storeName(storeName)
                .introduction(introduction)
                .status(SellerStatus.ACTIVE)
                .approvedAt(LocalDateTime.now())
                .build();
    }

    public void updateStore(
            String storeName,
            String introduction
    ) {
        this.storeName = storeName;
        this.introduction = introduction;
    }

    public void suspend() {
        this.status = SellerStatus.SUSPENDED;
    }

    public void activate() {
        this.status = SellerStatus.ACTIVE;
    }

    public void withdraw() {
        this.status = SellerStatus.WITHDRAWN;
    }
}