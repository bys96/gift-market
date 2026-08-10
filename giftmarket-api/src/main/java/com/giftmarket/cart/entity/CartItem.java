package com.giftmarket.cart.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "cart_items",
        indexes = {
                @Index(
                        name = "idx_cart_items_user_id",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_cart_items_product_id",
                        columnList = "product_id"
                ),
                @Index(
                        name = "idx_cart_items_variant_id",
                        columnList = "variant_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "product_id",
            nullable = false
    )
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    private Integer quantity;

    private CartItem(
            User user,
            Product product,
            ProductVariant variant,
            Integer quantity
    ) {
        this.user = user;
        this.product = product;
        this.variant = variant;
        this.quantity = quantity;
    }

    public static CartItem create(
            User user,
            Product product,
            ProductVariant variant,
            Integer quantity
    ) {
        return new CartItem(
                user,
                product,
                variant,
                quantity
        );
    }

    public void increaseQuantity(Integer quantity) {
        this.quantity += quantity;
    }

    public void changeQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}