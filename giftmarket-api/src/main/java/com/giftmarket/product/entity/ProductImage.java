package com.giftmarket.product.entity;

import com.giftmarket.global.entity.BaseEntity;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "product_images",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_product_images_product_id_sort_order",
                        columnNames = {"product_id", "sort_order"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_product_images_product_id",
                        columnList = "product_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "product_id",
            nullable = false
    )
    private Product product;

    @Column(
            name = "object_key",
            nullable = false,
            length = 1000
    )
    private String objectKey;

    @Column(
            name = "sort_order",
            nullable = false
    )
    private Integer sortOrder;

    @Builder
    private ProductImage(
            Product product,
            String objectKey,
            Integer sortOrder
    ) {
        this.product = product;
        this.objectKey = objectKey;
        this.sortOrder = sortOrder;
    }

    public static ProductImage create(
            Product product,
            String objectKey,
            Integer sortOrder
    ) {
        return ProductImage.builder()
                .product(product)
                .objectKey(objectKey)
                .sortOrder(sortOrder)
                .build();
    }

    public void changeSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}