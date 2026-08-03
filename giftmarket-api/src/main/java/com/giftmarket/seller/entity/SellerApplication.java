package com.giftmarket.seller.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "seller_applications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
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

    @Column(
            name = "introduction",
            length = 1000
    )
    private String introduction;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private SellerApplicationStatus status;

    @Column(
            name = "rejection_reason",
            length = 500
    )
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Builder
    private SellerApplication(
            User user,
            String storeName,
            String introduction,
            SellerApplicationStatus status
    ) {
        this.user = user;
        this.storeName = storeName;
        this.introduction = introduction;
        this.status = status;
    }

    public static SellerApplication create(
            User user,
            String storeName,
            String introduction
    ) {
        return SellerApplication.builder()
                .user(user)
                .storeName(storeName)
                .introduction(introduction)
                .status(SellerApplicationStatus.PENDING)
                .build();
    }

    public void approve(Long reviewerId) {
        this.status = SellerApplicationStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = null;
    }

    public void reject(
            Long reviewerId,
            String rejectionReason
    ) {
        this.status = SellerApplicationStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = rejectionReason;
    }
}