package com.giftmarket.order.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.user.entity.User;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_orders_order_number",
                        columnNames = "order_number"
                )
        },
        indexes = {
                @Index(
                        name = "idx_orders_user_id_created_at",
                        columnList = "user_id, created_at"
                ),
                @Index(
                        name = "idx_orders_status_created_at",
                        columnList = "status, created_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "order_number",
            nullable = false,
            length = 50
    )
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private OrderStatus status;

    @Column(
            name = "total_product_amount",
            nullable = false
    )
    private Long totalProductAmount;

    @Column(
            name = "total_shipping_fee",
            nullable = false
    )
    private Long totalShippingFee;

    @Column(
            name = "total_amount",
            nullable = false
    )
    private Long totalAmount;

    @Column(
            name = "recipient_name",
            nullable = false,
            length = 100
    )
    private String recipientName;

    @Column(
            name = "recipient_phone",
            nullable = false,
            length = 30
    )
    private String recipientPhone;

    @Column(
            name = "postal_code",
            nullable = false,
            length = 20
    )
    private String postalCode;

    @Column(
            nullable = false,
            length = 500
    )
    private String address;

    @Column(
            name = "address_detail",
            length = 500
    )
    private String addressDetail;

    @Column(
            name = "ordered_at"
    )
    private LocalDateTime orderedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Builder
    private Order(
            String orderNumber,
            User user,
            OrderStatus status,
            Long totalProductAmount,
            Long totalShippingFee,
            Long totalAmount,
            String recipientName,
            String recipientPhone,
            String postalCode,
            String address,
            String addressDetail,
            LocalDateTime orderedAt
    ) {
        this.orderNumber = orderNumber;
        this.user = user;
        this.status = status;
        this.totalProductAmount = totalProductAmount;
        this.totalShippingFee = totalShippingFee;
        this.totalAmount = totalAmount;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.postalCode = postalCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.orderedAt = orderedAt;
    }

    public static Order create(
            String orderNumber,
            User user,
            Long totalProductAmount,
            Long totalShippingFee,
            String recipientName,
            String recipientPhone,
            String postalCode,
            String address,
            String addressDetail
    ) {
        return Order.builder()
                .orderNumber(orderNumber)
                .user(user)
                .status(OrderStatus.ORDERED)
                .totalProductAmount(totalProductAmount)
                .totalShippingFee(totalShippingFee)
                .totalAmount(
                        totalProductAmount + totalShippingFee
                )
                .recipientName(recipientName)
                .recipientPhone(recipientPhone)
                .postalCode(postalCode)
                .address(address)
                .addressDetail(addressDetail)
                .orderedAt(LocalDateTime.now())
                .build();
    }

    public static Order createPendingPayment(
            String orderNumber,
            User user,
            Long totalProductAmount,
            Long totalShippingFee,
            String recipientName,
            String recipientPhone,
            String postalCode,
            String address,
            String addressDetail
    ) {
        return Order.builder()
                .orderNumber(orderNumber)
                .user(user)
                .status(OrderStatus.PENDING_PAYMENT)
                .totalProductAmount(totalProductAmount)
                .totalShippingFee(totalShippingFee)
                .totalAmount(
                        totalProductAmount + totalShippingFee
                )
                .recipientName(recipientName)
                .recipientPhone(recipientPhone)
                .postalCode(postalCode)
                .address(address)
                .addressDetail(addressDetail)
                .orderedAt(null)
                .build();
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void markPaid(LocalDateTime approvedAt) {
        this.status = OrderStatus.PAID;
        this.orderedAt = approvedAt;
    }

    public void markPaymentExpired() {
        this.status = OrderStatus.PAYMENT_EXPIRED;
    }

    public void markPaymentFailed() {
        this.status = OrderStatus.PAYMENT_FAILED;
    }
}
