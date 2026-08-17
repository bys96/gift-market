package com.giftmarket.order.entity;

import com.giftmarket.global.entity.BaseEntity;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "order_cancellation_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_order_cancellation_items_cancellation_item",
                        columnNames = {"order_cancellation_id", "order_item_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_order_cancellation_items_order_item",
                        columnList = "order_item_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCancellationItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_cancellation_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_cancellation_items_cancellation")
    )
    private OrderCancellation orderCancellation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_item_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_cancellation_items_order_item")
    )
    private OrderItem orderItem;

    @Column(nullable = false)
    private int quantity;

    private OrderCancellationItem(
            OrderCancellation orderCancellation,
            OrderItem orderItem,
            int quantity
    ) {
        if (orderCancellation == null || orderItem == null) {
            throw new IllegalArgumentException("취소 대상 주문 상품이 필요합니다.");
        }
        if (orderCancellation.getSellerOrder() != orderItem.getSellerOrder()) {
            throw new IllegalArgumentException("취소 요청과 주문 상품의 판매자 주문이 일치하지 않습니다.");
        }
        if (quantity <= 0 || quantity > orderItem.getRemainingQuantity()) {
            throw new IllegalArgumentException("취소 수량이 주문 상품의 취소 가능 수량을 벗어났습니다.");
        }
        this.orderCancellation = orderCancellation;
        this.orderItem = orderItem;
        this.quantity = quantity;
    }

    public static OrderCancellationItem create(
            OrderCancellation orderCancellation,
            OrderItem orderItem,
            int quantity
    ) {
        return new OrderCancellationItem(orderCancellation, orderItem, quantity);
    }
}
