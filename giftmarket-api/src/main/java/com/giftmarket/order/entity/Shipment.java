package com.giftmarket.order.entity;

import com.giftmarket.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "shipments",
        indexes = {
                @Index(
                        name = "idx_shipments_seller_order_type",
                        columnList = "seller_order_id, shipment_type"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "seller_order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_shipments_seller_order")
    )
    private SellerOrder sellerOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_type", nullable = false, length = 30)
    private ShipmentType type;

    @Column(name = "shipping_company", nullable = false, length = 100)
    private String shippingCompany;

    @Column(name = "tracking_number", nullable = false, length = 100)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    private Shipment(
            SellerOrder sellerOrder,
            ShipmentType type,
            String shippingCompany,
            String trackingNumber,
            ShipmentStatus status,
            LocalDateTime shippedAt
    ) {
        this.sellerOrder = sellerOrder;
        this.type = type;
        this.shippingCompany = shippingCompany;
        this.trackingNumber = trackingNumber;
        this.status = status;
        this.shippedAt = shippedAt;
    }

    public static Shipment createReady(
            SellerOrder sellerOrder,
            ShipmentType type,
            String shippingCompany,
            String trackingNumber
    ) {
        validateRequiredFields(sellerOrder, type, shippingCompany, trackingNumber);
        return new Shipment(
                sellerOrder,
                type,
                shippingCompany,
                trackingNumber,
                ShipmentStatus.READY,
                null
        );
    }

    public static Shipment createShipped(
            SellerOrder sellerOrder,
            ShipmentType type,
            String shippingCompany,
            String trackingNumber,
            LocalDateTime shippedAt
    ) {
        validateRequiredFields(sellerOrder, type, shippingCompany, trackingNumber);
        if (shippedAt == null) {
            throw new IllegalArgumentException("배송 정보를 확인해주세요.");
        }
        return new Shipment(
                sellerOrder,
                type,
                shippingCompany,
                trackingNumber,
                ShipmentStatus.SHIPPED,
                shippedAt
        );
    }

    public void ship(LocalDateTime shippedAt) {
        if (status == ShipmentStatus.SHIPPED) {
            return;
        }
        if (status != ShipmentStatus.READY || shippedAt == null) {
            throw new IllegalStateException("준비 중인 배송만 출고 처리할 수 있습니다.");
        }
        status = ShipmentStatus.SHIPPED;
        this.shippedAt = shippedAt;
    }

    public void deliver(LocalDateTime deliveredAt) {
        if (status == ShipmentStatus.DELIVERED) {
            return;
        }
        if (status != ShipmentStatus.SHIPPED || deliveredAt == null) {
            throw new IllegalStateException("출고된 배송만 배송 완료 처리할 수 있습니다.");
        }
        status = ShipmentStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
    }

    public void cancel() {
        if (status == ShipmentStatus.CANCELED) {
            return;
        }
        if (status != ShipmentStatus.READY) {
            throw new IllegalStateException("준비 중인 배송만 취소할 수 있습니다.");
        }
        status = ShipmentStatus.CANCELED;
    }

    private static void validateRequiredFields(
            SellerOrder sellerOrder,
            ShipmentType type,
            String shippingCompany,
            String trackingNumber
    ) {
        if (sellerOrder == null || type == null
                || shippingCompany == null || shippingCompany.isBlank()
                || trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("배송 정보를 확인해주세요.");
        }
    }
}
