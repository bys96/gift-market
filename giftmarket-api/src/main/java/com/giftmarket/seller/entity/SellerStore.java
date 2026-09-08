package com.giftmarket.seller.entity;

import com.giftmarket.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "seller_stores", uniqueConstraints = {
        @UniqueConstraint(name = "uk_seller_store_seller", columnNames = "seller_id"),
        @UniqueConstraint(name = "uk_seller_store_name", columnNames = "store_name")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerStore extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false, foreignKey = @ForeignKey(name = "fk_seller_store_seller"))
    private Seller seller;
    @Column(name = "store_name", nullable = false, length = 30) private String storeName;
    @Column(length = 500) private String introduction;
    @Column(name = "logo_image_key", length = 500) private String logoImageKey;
    @Column(name = "banner_image_key", length = 500) private String bannerImageKey;
    @Column(name = "customer_service_phone", length = 30) private String customerServicePhone;
    @Column(name = "customer_service_email", length = 255) private String customerServiceEmail;
    @Column(name = "customer_service_hours", length = 255) private String customerServiceHours;
    @Column(name = "shipping_guide", length = 1000) private String shippingGuide;
    @Column(name = "return_exchange_guide", length = 1000) private String returnExchangeGuide;

    public static SellerStore create(Seller seller, String storeName, String introduction) {
        SellerStore store = new SellerStore(); store.seller = seller; store.storeName = storeName; store.introduction = introduction; return store;
    }
    public void update(String storeName, String introduction, String logoImageKey, String bannerImageKey,
                       String phone, String email, String hours, String shippingGuide, String returnExchangeGuide) {
        this.storeName = storeName; this.introduction = introduction; this.logoImageKey = logoImageKey;
        this.bannerImageKey = bannerImageKey; this.customerServicePhone = phone; this.customerServiceEmail = email;
        this.customerServiceHours = hours; this.shippingGuide = shippingGuide; this.returnExchangeGuide = returnExchangeGuide;
    }
}
