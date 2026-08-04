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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "product_variants",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_product_variants_sku_code",
                        columnNames = "sku_code"
                ),
                @UniqueConstraint(
                        name = "uk_product_variants_product_id_combination_key",
                        columnNames = {
                                "product_id",
                                "combination_key"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_product_variants_product_id_active",
                        columnList = "product_id, active"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "product_id",
            nullable = false
    )
    private Product product;

    @Column(
            name = "sku_code",
            nullable = false,
            length = 100
    )
    private String skuCode;

    @Column(
            name = "combination_key",
            nullable = false,
            length = 500
    )
    private String combinationKey;

    @Column(
            name = "additional_price",
            nullable = false
    )
    private Long additionalPrice;

    @Column(
            name = "stock_quantity",
            nullable = false
    )
    private Integer stockQuantity;

    @Column(nullable = false)
    private boolean active;

    @Builder
    private ProductVariant(
            Product product,
            String skuCode,
            String combinationKey,
            Long additionalPrice,
            Integer stockQuantity,
            boolean active
    ) {
        this.product = product;
        this.skuCode = skuCode;
        this.combinationKey = combinationKey;
        this.additionalPrice = additionalPrice;
        this.stockQuantity = stockQuantity;
        this.active = active;
    }

    public static ProductVariant create(
            Product product,
            String skuCode,
            String combinationKey,
            Long additionalPrice,
            Integer stockQuantity
    ) {
        return ProductVariant.builder()
                .product(product)
                .skuCode(skuCode)
                .combinationKey(combinationKey)
                .additionalPrice(additionalPrice)
                .stockQuantity(stockQuantity)
                .active(true)
                .build();
    }

    public void update(
            String skuCode,
            String combinationKey,
            Long additionalPrice,
            Integer stockQuantity
    ) {
        this.skuCode = skuCode;
        this.combinationKey = combinationKey;
        this.additionalPrice = additionalPrice;
        this.stockQuantity = stockQuantity;
    }

    public void changeStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public void decreaseStock(Integer quantity) {
        this.stockQuantity -= quantity;
    }

    public void increaseStock(Integer quantity) {
        this.stockQuantity += quantity;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}