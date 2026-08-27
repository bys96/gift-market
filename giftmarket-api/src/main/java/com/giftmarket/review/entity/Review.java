package com.giftmarket.review.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(name = "uk_reviews_order_item", columnNames = "order_item_id"),
        indexes = {
                @Index(name = "idx_reviews_product_active_created", columnList = "product_id, deleted_at, created_at"),
                @Index(name = "idx_reviews_user", columnList = "user_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Check(constraints = "rating between 1 and 5")
public class Review extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reviews_user"))
    private User user;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_item_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reviews_order_item"))
    private OrderItem orderItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reviews_product"))
    private Product product;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "variant_id", foreignKey = @ForeignKey(name = "fk_reviews_variant"))
    private ProductVariant variant;
    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productNameSnapshot;
    @Column(name = "option_snapshot", length = 1000)
    private String optionSnapshot;
    @Column(name = "unit_price_snapshot", nullable = false)
    private long unitPriceSnapshot;
    @Column(nullable = false)
    private int rating;
    @Column(nullable = false, length = 2000)
    private String content;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Review(User user, OrderItem orderItem, Product product, ProductVariant variant,
                   String productNameSnapshot, String optionSnapshot, long unitPriceSnapshot,
                   int rating, String content) {
        this.user = user; this.orderItem = orderItem; this.product = product; this.variant = variant;
        applyTarget(productNameSnapshot, optionSnapshot, unitPriceSnapshot);
        update(rating, content);
    }

    public static Review create(User user, OrderItem orderItem, Product product, ProductVariant variant,
                                String productNameSnapshot, String optionSnapshot, long unitPriceSnapshot,
                                int rating, String content) {
        return new Review(user, orderItem, product, variant, productNameSnapshot, optionSnapshot,
                unitPriceSnapshot, rating, content);
    }

    public void restore(Product product, ProductVariant variant, String productNameSnapshot,
                        String optionSnapshot, long unitPriceSnapshot, int rating, String content) {
        this.product = product; this.variant = variant;
        applyTarget(productNameSnapshot, optionSnapshot, unitPriceSnapshot);
        update(rating, content); deletedAt = null;
    }

    public void update(int rating, String content) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("별점은 1점부터 5점까지 선택해주세요.");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("리뷰 내용을 입력해주세요.");
        String trimmed = content.trim();
        if (trimmed.length() > 2000) throw new IllegalArgumentException("리뷰 내용은 2000자 이하로 입력해주세요.");
        this.rating = rating; this.content = trimmed;
    }

    public void delete(LocalDateTime deletedAt) {
        if (deletedAt == null) throw new IllegalArgumentException("삭제 시각이 필요합니다.");
        this.deletedAt = deletedAt;
    }

    private void applyTarget(String name, String option, long price) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("리뷰 상품명이 필요합니다.");
        if (price <= 0) throw new IllegalArgumentException("리뷰 상품 가격이 필요합니다.");
        this.productNameSnapshot = name.trim();
        this.optionSnapshot = option == null || option.isBlank() ? null : option.trim();
        this.unitPriceSnapshot = price;
    }
}
