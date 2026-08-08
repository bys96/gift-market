package com.giftmarket.cart.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.product.entity.Product;
import com.giftmarket.user.entity.User;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cart_items_user_product",
                        columnNames = {
                                "user_id",
                                "product_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_cart_items_user_id",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_cart_items_product_id",
                        columnList = "product_id"
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

    @Column(nullable = false)
    private Integer quantity;

    private CartItem(
            User user,
            Product product,
            Integer quantity
    ) {
        this.user = user;
        this.product = product;
        this.quantity = quantity;
    }

    public static CartItem create(
            User user,
            Product product,
            Integer quantity
    ) {
        return new CartItem(
                user,
                product,
                quantity
        );
    }

    public void increaseQuantity(
            Integer quantity,
            Integer stockQuantity
    ) {
        this.quantity = Math.min(
                this.quantity + quantity,
                stockQuantity
        );
    }

    public void changeQuantity(
            Integer quantity
    ) {
        this.quantity = quantity;
    }
}