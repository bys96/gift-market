package com.giftmarket.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "product_gift_occasions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_gift_occasions_product_occasion",
                columnNames = {"product_id", "gift_occasion_id"}
        ),
        indexes = @Index(
                name = "idx_product_gift_occasions_occasion_id",
                columnList = "gift_occasion_id"
        )
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductGiftOccasion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gift_occasion_id", nullable = false)
    private GiftOccasion giftOccasion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ProductGiftOccasion(Product product, GiftOccasion giftOccasion) {
        this.product = product;
        this.giftOccasion = giftOccasion;
    }

    public static ProductGiftOccasion create(Product product, GiftOccasion giftOccasion) {
        return new ProductGiftOccasion(product, giftOccasion);
    }
}
