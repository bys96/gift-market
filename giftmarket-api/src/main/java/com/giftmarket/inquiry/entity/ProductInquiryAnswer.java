package com.giftmarket.inquiry.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.seller.entity.Seller;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_inquiry_answers", uniqueConstraints = @UniqueConstraint(
        name = "uk_product_inquiry_answers_inquiry", columnNames = "inquiry_id"
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInquiryAnswer extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inquiry_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_inquiry_answers_inquiry"))
    private ProductInquiry inquiry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_inquiry_answers_seller"))
    private Seller seller;

    @Column(nullable = false, length = 2000) private String content;

    private ProductInquiryAnswer(ProductInquiry inquiry, Seller seller, String content) {
        this.inquiry = inquiry; this.seller = seller; this.content = content;
    }

    public static ProductInquiryAnswer create(ProductInquiry inquiry, Seller seller, String content) {
        return new ProductInquiryAnswer(inquiry, seller, content);
    }

    public void updateContent(String content) { this.content = content; }
}
