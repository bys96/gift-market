package com.giftmarket.review.entity;

import com.giftmarket.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "review_images", uniqueConstraints = @UniqueConstraint(name = "uk_review_images_review_object", columnNames = {"review_id", "object_key"}),
        indexes = @Index(name = "idx_review_images_review_sort", columnList = "review_id, sort_order"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImage extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "review_id", nullable = false, foreignKey = @ForeignKey(name = "fk_review_images_review"))
    private Review review;
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private ReviewImage(Review review, String objectKey, int sortOrder) {
        if (review == null || objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("리뷰 이미지 정보가 필요합니다.");
        if (sortOrder < 0 || sortOrder > 4) throw new IllegalArgumentException("리뷰 이미지 순서를 확인해주세요.");
        this.review = review; this.objectKey = objectKey.trim(); this.sortOrder = sortOrder;
    }
    public static ReviewImage create(Review review, String objectKey, int sortOrder) { return new ReviewImage(review, objectKey, sortOrder); }
}
