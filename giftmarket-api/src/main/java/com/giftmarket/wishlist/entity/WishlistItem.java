package com.giftmarket.wishlist.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.product.entity.Product;
import com.giftmarket.user.entity.User;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "wishlist_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wishlist_items_user_product",
                columnNames = {"user_id", "product_id"}
        ),
        indexes = @Index(
                name = "idx_wishlist_items_user_created_at",
                columnList = "user_id, created_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishlistItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_wishlist_items_user")
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_wishlist_items_product")
    )
    private Product product;

    private WishlistItem(User user, Product product) {
        this.user = user;
        this.product = product;
    }

    public static WishlistItem create(User user, Product product) {
        return new WishlistItem(user, product);
    }
}
