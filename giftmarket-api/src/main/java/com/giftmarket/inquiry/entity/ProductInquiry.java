package com.giftmarket.inquiry.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.product.entity.Product;
import com.giftmarket.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_inquiries", indexes = {
        @Index(name = "idx_product_inquiries_product_created", columnList = "product_id, created_at"),
        @Index(name = "idx_product_inquiries_user", columnList = "user_id"),
        @Index(name = "idx_product_inquiries_status_created", columnList = "status, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInquiry extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_inquiries_product"))
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_inquiries_user"))
    private User user;

    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, length = 2000) private String content;
    @Column(name = "is_private", nullable = false) private boolean privateInquiry;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ProductInquiryStatus status;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;

    private ProductInquiry(Product product, User user, String title, String content, boolean privateInquiry) {
        this.product = product;
        this.user = user;
        this.title = title;
        this.content = content;
        this.privateInquiry = privateInquiry;
        this.status = ProductInquiryStatus.WAITING;
    }

    public static ProductInquiry create(Product product, User user, String title, String content, boolean privateInquiry) {
        return new ProductInquiry(product, user, title, content, privateInquiry);
    }

    public void updateQuestion(String title, String content, boolean privateInquiry) {
        ensureWaiting();
        this.title = title;
        this.content = content;
        this.privateInquiry = privateInquiry;
    }

    public void markAnswered() {
        this.status = ProductInquiryStatus.ANSWERED;
    }

    public void softDelete() {
        if (deletedAt == null) deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    public void ensureWaiting() {
        if (status != ProductInquiryStatus.WAITING) {
            throw new IllegalStateException("답변 완료 문의는 수정할 수 없습니다.");
        }
    }
}
