package com.giftmarket.product.draft.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "product_drafts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_product_drafts_seller_product",
                        columnNames = {
                                "seller_id",
                                "product_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_product_drafts_seller_id_updated_at",
                        columnList = "seller_id, updated_at"
                ),
                @Index(
                        name = "idx_product_drafts_product_id",
                        columnList = "product_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductDraft extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "seller_id",
            nullable = false
    )
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(
            name = "draft_data",
            nullable = false,
            columnDefinition = "json"
    )
    private String draftData;

    @Builder
    private ProductDraft(
            Seller seller,
            Product product,
            String draftData
    ) {
        this.seller = seller;
        this.product = product;
        this.draftData = draftData;
    }

    public static ProductDraft create(
            Seller seller,
            Product product,
            String draftData
    ) {
        return ProductDraft.builder()
                .seller(seller)
                .product(product)
                .draftData(draftData)
                .build();
    }

    public void updateDraftData(String draftData) {
        this.draftData = draftData;
    }
}