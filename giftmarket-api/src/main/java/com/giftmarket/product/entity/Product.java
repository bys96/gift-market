package com.giftmarket.product.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.seller.entity.Seller;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "products",
        indexes = {
                @Index(
                        name = "idx_products_seller_id_status",
                        columnList = "seller_id, status"
                ),
                @Index(
                        name = "idx_products_category_id_status",
                        columnList = "category_id, status"
                ),
                @Index(
                        name = "idx_products_status_created_at",
                        columnList = "status, created_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

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
    @JoinColumn(
            name = "category_id",
            nullable = false
    )
    private Category category;

    @Column(
            nullable = false,
            length = 200
    )
    private String name;

    @Column(
            name = "brand_name",
            length = 100
    )
    private String brandName;

    @Column(length = 500)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Long price;

    @Column(
            name = "stock_quantity",
            nullable = false
    )
    private Integer stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private ProductStatus status;

    @Column(
            name = "representative_image_key",
            length = 1000
    )
    private String representativeImageKey;

    @Column(
            name = "free_shipping",
            nullable = false
    )
    private boolean freeShipping;

    @Column(
            name = "shipping_fee",
            nullable = false
    )
    private Long shippingFee;

    @Builder
    private Product(
            Seller seller,
            Category category,
            String name,
            String brandName,
            String summary,
            String description,
            Long price,
            Integer stockQuantity,
            ProductStatus status,
            String representativeImageKey,
            boolean freeShipping,
            Long shippingFee
    ) {
        this.seller = seller;
        this.category = category;
        this.name = name;
        this.brandName = brandName;
        this.summary = summary;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.status = status;
        this.representativeImageKey = representativeImageKey;
        this.freeShipping = freeShipping;
        this.shippingFee = resolveShippingFee(
                freeShipping,
                shippingFee
        );
    }

    public static Product createDraft(
            Seller seller,
            Category category,
            String name,
            String brandName,
            String summary,
            String description,
            Long price,
            Integer stockQuantity,
            String representativeImageKey,
            boolean freeShipping,
            Long shippingFee
    ) {
        return Product.builder()
                .seller(seller)
                .category(category)
                .name(name)
                .brandName(brandName)
                .summary(summary)
                .description(description)
                .price(price)
                .stockQuantity(stockQuantity)
                .status(ProductStatus.DRAFT)
                .representativeImageKey(representativeImageKey)
                .freeShipping(freeShipping)
                .shippingFee(shippingFee)
                .build();
    }

    public void update(
            Category category,
            String name,
            String brandName,
            String summary,
            String description,
            Long price,
            Integer stockQuantity,
            String representativeImageKey,
            boolean freeShipping,
            Long shippingFee
    ) {
        this.category = category;
        this.name = name;
        this.brandName = brandName;
        this.summary = summary;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.representativeImageKey = representativeImageKey;
        this.freeShipping = freeShipping;
        this.shippingFee = resolveShippingFee(
                freeShipping,
                shippingFee
        );

        synchronizeStatusWithStock();
    }

    public void startSale() {
        this.status = hasStock()
                ? ProductStatus.ON_SALE
                : ProductStatus.SOLD_OUT;
    }

    public void hide() {
        this.status = ProductStatus.HIDDEN;
    }

    public void changeToDraft() {
        this.status = ProductStatus.DRAFT;
    }

    public void changeStatus(ProductStatus status) {
        if (status == ProductStatus.ON_SALE) {
            startSale();
            return;
        }

        if (status == ProductStatus.HIDDEN) {
            hide();
            return;
        }

        if (status == ProductStatus.DRAFT) {
            changeToDraft();
            return;
        }

        this.status = ProductStatus.SOLD_OUT;
    }

    public void changeStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
        synchronizeStatusWithStock();
    }

    public void decreaseStock(Integer quantity) {
        this.stockQuantity -= quantity;
        synchronizeStatusWithStock();
    }

    public void increaseStock(Integer quantity) {
        this.stockQuantity += quantity;
        synchronizeStatusWithStock();
    }

    public boolean hasStock() {
        return stockQuantity > 0;
    }

    private void synchronizeStatusWithStock() {
        if (status == ProductStatus.ON_SALE && !hasStock()) {
            this.status = ProductStatus.SOLD_OUT;
            return;
        }

        if (status == ProductStatus.SOLD_OUT && hasStock()) {
            this.status = ProductStatus.ON_SALE;
        }
    }

    private static Long resolveShippingFee(
            boolean freeShipping,
            Long shippingFee
    ) {
        return freeShipping ? 0L : shippingFee;
    }
}