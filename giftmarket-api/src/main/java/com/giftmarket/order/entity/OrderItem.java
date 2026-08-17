package com.giftmarket.order.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.seller.entity.Seller;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "order_items",
        indexes = {
                @Index(
                        name = "idx_order_items_order_id",
                        columnList = "order_id"
                ),
                @Index(
                        name = "idx_order_items_product_id",
                        columnList = "product_id"
                ),
                @Index(
                        name = "idx_order_items_seller_id",
                        columnList = "seller_id"
                ),
                @Index(
                        name = "idx_order_items_seller_order_id",
                        columnList = "seller_order_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "order_id",
            nullable = false
    )
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "product_id",
            nullable = false
    )
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "seller_id",
            nullable = false
    )
    private Seller seller;

    /*
     * 주문 준비 시점에 주문 당시 Seller snapshot 기준으로 생성된
     * 판매자별 주문 처리 단위와 반드시 연결됩니다.
     */
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "seller_order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_items_seller_order")
    )
    private SellerOrder sellerOrder;

    /*
     * 장바구니 주문의 원본 CartItem ID Snapshot.
     *
     * 결제 완료 후 정확한 장바구니 항목만 제거하기 위한 값이며
     * CartItem 생명주기와 분리하기 위해 FK로 연결하지 않습니다.
     * 장바구니 주문 준비에서는 원본 ID를 저장하고 바로구매는 null입니다.
     */
    @Column(name = "source_cart_item_id")
    private Long sourceCartItemId;

    @Column(
            name = "product_name",
            nullable = false,
            length = 200
    )
    private String productName;

    @Column(
            name = "brand_name",
            length = 100
    )
    private String brandName;

    @Column(
            name = "store_name",
            nullable = false,
            length = 100
    )
    private String storeName;

    @Column(
            name = "representative_image_key",
            length = 1000
    )
    private String representativeImageKey;

    /*
     * 주문 당시 옵션 표시값 Snapshot.
     * 예: "색상: 화이트 / 사이즈: XL"
     *
     * 옵션이 없는 상품은 null.
     */
    @Column(
            name = "option_snapshot",
            length = 1000
    )
    private String optionSnapshot;

    @Column(
            name = "product_price",
            nullable = false
    )
    private Long productPrice;

    @Column(
            name = "additional_price",
            nullable = false
    )
    private Long additionalPrice;

    @Column(
            name = "unit_price",
            nullable = false
    )
    private Long unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(
            name = "total_price",
            nullable = false
    )
    private Long totalPrice;

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
    private OrderItem(
            Order order,
            Product product,
            ProductVariant variant,
            Seller seller,
            SellerOrder sellerOrder,
            Long sourceCartItemId,
            String productName,
            String brandName,
            String storeName,
            String representativeImageKey,
            String optionSnapshot,
            Long productPrice,
            Long additionalPrice,
            Integer quantity,
            boolean freeShipping,
            Long shippingFee
    ) {
        this.order = order;
        this.product = product;
        this.variant = variant;
        this.seller = seller;
        this.sellerOrder = sellerOrder;
        this.sourceCartItemId = sourceCartItemId;
        this.productName = productName;
        this.brandName = brandName;
        this.storeName = storeName;
        this.representativeImageKey = representativeImageKey;
        this.optionSnapshot = optionSnapshot;
        this.productPrice = productPrice;
        this.additionalPrice = additionalPrice;
        this.unitPrice = productPrice + additionalPrice;
        this.quantity = quantity;
        this.totalPrice = this.unitPrice * quantity;
        this.freeShipping = freeShipping;
        this.shippingFee = freeShipping
                ? 0L
                : shippingFee;
    }

    public static OrderItem create(
            Order order,
            Product product,
            ProductVariant variant,
            Seller seller,
            SellerOrder sellerOrder,
            Long sourceCartItemId,
            String productName,
            String brandName,
            String storeName,
            String representativeImageKey,
            String optionSnapshot,
            Long productPrice,
            Long additionalPrice,
            Integer quantity,
            boolean freeShipping,
            Long shippingFee
    ) {
        return OrderItem.builder()
                .order(order)
                .product(product)
                .variant(variant)
                .seller(seller)
                .sellerOrder(sellerOrder)
                .sourceCartItemId(sourceCartItemId)
                .productName(productName)
                .brandName(brandName)
                .storeName(storeName)
                .representativeImageKey(representativeImageKey)
                .optionSnapshot(optionSnapshot)
                .productPrice(productPrice)
                .additionalPrice(additionalPrice)
                .quantity(quantity)
                .freeShipping(freeShipping)
                .shippingFee(shippingFee)
                .build();
    }
}
