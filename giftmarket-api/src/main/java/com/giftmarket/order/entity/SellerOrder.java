package com.giftmarket.order.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.seller.entity.Seller;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "seller_orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_seller_orders_order_seller",
                        columnNames = {"order_id", "seller_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_seller_orders_seller_status_created",
                        columnList = "seller_id, status, created_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_seller_orders_order")
    )
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "seller_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_seller_orders_seller")
    )
    private Seller seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SellerOrderStatus status;

    @Column(name = "shipping_company", length = 100)
    private String shippingCompany;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "prepared_at")
    private LocalDateTime preparedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    private SellerOrder(
            Order order,
            Seller seller,
            SellerOrderStatus status
    ) {
        this.order = order;
        this.seller = seller;
        this.status = status;
    }

    public static SellerOrder createPendingPayment(
            Order order,
            Seller seller
    ) {
        return new SellerOrder(
                order,
                seller,
                SellerOrderStatus.PENDING_PAYMENT
        );
    }

    public void markPaid() {
        if (status == SellerOrderStatus.PAID) {
            return;
        }
        if (status != SellerOrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(
                    "결제 대기 중인 판매자 주문만 결제 완료 처리할 수 있습니다."
            );
        }
        status = SellerOrderStatus.PAID;
    }

    public void cancel() {
        if (status == SellerOrderStatus.CANCELLED) {
            return;
        }
        status = SellerOrderStatus.CANCELLED;
    }

    public void prepare(LocalDateTime preparedAt) {
        if (status == SellerOrderStatus.PREPARING) {
            return;
        }
        validateTransition(SellerOrderStatus.PAID, SellerOrderStatus.PREPARING);
        status = SellerOrderStatus.PREPARING;
        this.preparedAt = preparedAt;
    }

    public void ship(
            String shippingCompany,
            String trackingNumber,
            LocalDateTime shippedAt
    ) {
        if (status == SellerOrderStatus.SHIPPED) {
            if (Objects.equals(this.shippingCompany, shippingCompany)
                    && Objects.equals(this.trackingNumber, trackingNumber)) {
                return;
            }
            throw new IllegalStateException("이미 다른 배송정보로 출고 처리되었습니다.");
        }
        validateTransition(SellerOrderStatus.PREPARING, SellerOrderStatus.SHIPPED);
        status = SellerOrderStatus.SHIPPED;
        this.shippingCompany = shippingCompany;
        this.trackingNumber = trackingNumber;
        this.shippedAt = shippedAt;
    }

    public void deliver(LocalDateTime deliveredAt) {
        if (status == SellerOrderStatus.DELIVERED) {
            return;
        }
        validateTransition(SellerOrderStatus.SHIPPED, SellerOrderStatus.DELIVERED);
        if (shippingCompany == null || trackingNumber == null) {
            throw new IllegalStateException("배송정보가 없는 주문은 배송완료 처리할 수 없습니다.");
        }
        status = SellerOrderStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
    }

    private void validateTransition(
            SellerOrderStatus expected,
            SellerOrderStatus target
    ) {
        if (status != expected) {
            throw new IllegalStateException(
                    status + " 상태에서는 " + target + " 상태로 변경할 수 없습니다."
            );
        }
    }
}
