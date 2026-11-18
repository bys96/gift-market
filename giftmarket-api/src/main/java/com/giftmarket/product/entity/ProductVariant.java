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
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "product_variants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_variants_product_seller_sku",
                        columnNames = {"product_id", "seller_sku_code"}),
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
    // Reserved identity; the existing product/combination UNIQUE permits exactly one.
    public static final String DEFAULT_COMBINATION_KEY = "__DEFAULT__";

    public boolean isDefaultVariant() {
        return DEFAULT_COMBINATION_KEY.equals(combinationKey);
    }

    public static ProductVariant createDefault(Product product) {
        return create(product, "DEFAULT-" + product.getId(), DEFAULT_COMBINATION_KEY,
                0L, product.getStockQuantity());
    }

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
            updatable = false,
            nullable = false,
            length = 100
    )
    private String skuCode;

    @Column(name = "seller_sku_code", nullable = false, length = 100)
    private String sellerSkuCode;

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
            String sellerSkuCode,
            String combinationKey,
            Long additionalPrice,
            Integer stockQuantity,
            boolean active
    ) {
        this.product = product;
        this.skuCode = "GM-" + UUID.randomUUID();
        this.sellerSkuCode = normalizeSellerSkuCode(sellerSkuCode);
        this.combinationKey = combinationKey;
        this.additionalPrice = additionalPrice;
        this.stockQuantity = stockQuantity;
        this.active = active;
    }

    public static ProductVariant create(
            Product product,
            String sellerSkuCode,
            String combinationKey,
            Long additionalPrice,
            Integer stockQuantity
    ) {
        return ProductVariant.builder()
                .product(product)
                .sellerSkuCode(sellerSkuCode)
                .combinationKey(combinationKey)
                .additionalPrice(additionalPrice)
                .stockQuantity(stockQuantity)
                .active(true)
                .build();
    }

    public void update(
            String sellerSkuCode,
            String combinationKey,
            Long additionalPrice
    ) {
        this.sellerSkuCode = normalizeSellerSkuCode(sellerSkuCode);
        this.combinationKey = combinationKey;
        this.additionalPrice = additionalPrice;
    }

    public static String normalizeSellerSkuCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new com.giftmarket.product.exception.ProductException("옵션 관리 코드를 입력해주세요.");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 100) {
            throw new com.giftmarket.product.exception.ProductException("옵션 관리 코드는 정규화 후 100자 이하입니다.");
        }
        return normalized;
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
