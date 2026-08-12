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
import java.time.LocalDateTime;

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

    private static final int DEFAULT_SHIPPING_PREPARATION_DAYS = 3;
    private static final long DEFAULT_RETURN_SHIPPING_FEE = 3_000L;
    private static final long DEFAULT_EXCHANGE_SHIPPING_FEE = 6_000L;

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

    @Column(columnDefinition = "LONGTEXT")
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

    /*
     * 기존 상품 데이터와의 호환을 위해 nullable로 유지합니다.
     * 기존 데이터가 null이면 getter에서 운영 기본값을 반환합니다.
     */
    @Column(name = "shipping_preparation_days")
    private Integer shippingPreparationDays;

    @Column(name = "return_shipping_fee")
    private Long returnShippingFee;

    @Column(name = "exchange_shipping_fee")
    private Long exchangeShippingFee;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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
            Long shippingFee,
            Integer shippingPreparationDays,
            Long returnShippingFee,
            Long exchangeShippingFee
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
        this.shippingPreparationDays = resolveShippingPreparationDays(
                shippingPreparationDays
        );
        this.returnShippingFee = resolveReturnShippingFee(
                returnShippingFee
        );
        this.exchangeShippingFee = resolveExchangeShippingFee(
                exchangeShippingFee
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
            Long shippingFee,
            Integer shippingPreparationDays,
            Long returnShippingFee,
            Long exchangeShippingFee
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
                .shippingPreparationDays(shippingPreparationDays)
                .returnShippingFee(returnShippingFee)
                .exchangeShippingFee(exchangeShippingFee)
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
            Long shippingFee,
            Integer shippingPreparationDays,
            Long returnShippingFee,
            Long exchangeShippingFee
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
        this.shippingPreparationDays = resolveShippingPreparationDays(
                shippingPreparationDays
        );
        this.returnShippingFee = resolveReturnShippingFee(
                returnShippingFee
        );
        this.exchangeShippingFee = resolveExchangeShippingFee(
                exchangeShippingFee
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

    public void softDelete() {
        if (deletedAt != null) {
            return;
        }

        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
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

    public Integer getShippingPreparationDays() {
        return resolveShippingPreparationDays(shippingPreparationDays);
    }

    public Long getReturnShippingFee() {
        return resolveReturnShippingFee(returnShippingFee);
    }

    public Long getExchangeShippingFee() {
        return resolveExchangeShippingFee(exchangeShippingFee);
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

    private static Integer resolveShippingPreparationDays(
            Integer shippingPreparationDays
    ) {
        return shippingPreparationDays == null
                ? DEFAULT_SHIPPING_PREPARATION_DAYS
                : shippingPreparationDays;
    }

    private static Long resolveReturnShippingFee(
            Long returnShippingFee
    ) {
        return returnShippingFee == null
                ? DEFAULT_RETURN_SHIPPING_FEE
                : returnShippingFee;
    }

    private static Long resolveExchangeShippingFee(
            Long exchangeShippingFee
    ) {
        return exchangeShippingFee == null
                ? DEFAULT_EXCHANGE_SHIPPING_FEE
                : exchangeShippingFee;
    }
}