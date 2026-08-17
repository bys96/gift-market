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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "order_cancellations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_order_cancellations_client_request_key",
                        columnNames = "client_request_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_order_cancellations_order_status",
                        columnList = "order_id, status"
                ),
                @Index(
                        name = "idx_order_cancellations_seller_order_status",
                        columnList = "seller_order_id, status"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCancellation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_cancellations_order")
    )
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "seller_order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_cancellations_seller_order")
    )
    private SellerOrder sellerOrder;

    @Column(name = "client_request_key", nullable = false, length = 100)
    private String clientRequestKey;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderCancellationStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processing_at")
    private LocalDateTime processingAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;

    private OrderCancellation(
            Order order,
            SellerOrder sellerOrder,
            String clientRequestKey,
            String reason,
            OrderCancellationStatus status,
            LocalDateTime requestedAt
    ) {
        validateOrderConsistency(order, sellerOrder);
        this.order = order;
        this.sellerOrder = sellerOrder;
        this.clientRequestKey = requireText(clientRequestKey, "취소 요청 키가 필요합니다.");
        this.reason = requireText(reason, "취소 사유가 필요합니다.");
        this.status = status;
        this.requestedAt = requestedAt;
    }

    public static OrderCancellation createRequested(
            Order order,
            SellerOrder sellerOrder,
            String clientRequestKey,
            String reason,
            LocalDateTime requestedAt
    ) {
        return new OrderCancellation(
                order,
                sellerOrder,
                clientRequestKey,
                reason,
                OrderCancellationStatus.REQUESTED,
                requestedAt
        );
    }

    public static OrderCancellation createProcessing(
            Order order,
            SellerOrder sellerOrder,
            String clientRequestKey,
            String reason,
            LocalDateTime requestedAt
    ) {
        OrderCancellation cancellation = new OrderCancellation(
                order,
                sellerOrder,
                clientRequestKey,
                reason,
                OrderCancellationStatus.PROCESSING,
                requestedAt
        );
        cancellation.processingAt = requestedAt;
        return cancellation;
    }

    public void startProcessing(LocalDateTime processedAt) {
        requireStatus(OrderCancellationStatus.REQUESTED);
        status = OrderCancellationStatus.PROCESSING;
        processingAt = processedAt;
    }

    public void complete(LocalDateTime completedAt) {
        requireStatus(OrderCancellationStatus.PROCESSING);
        status = OrderCancellationStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public void reject(String rejectedReason, LocalDateTime rejectedAt) {
        requireStatus(OrderCancellationStatus.REQUESTED);
        status = OrderCancellationStatus.REJECTED;
        this.rejectedReason = requireText(rejectedReason, "취소 거절 사유가 필요합니다.");
        this.rejectedAt = rejectedAt;
    }

    public void fail(LocalDateTime failedAt) {
        requireStatus(OrderCancellationStatus.PROCESSING);
        status = OrderCancellationStatus.FAILED;
        this.failedAt = failedAt;
    }

    private void requireStatus(OrderCancellationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    status + " 상태에서는 해당 취소 상태 전이를 수행할 수 없습니다."
            );
        }
    }

    private static void validateOrderConsistency(Order order, SellerOrder sellerOrder) {
        if (order == null || sellerOrder == null || sellerOrder.getOrder() != order) {
            throw new IllegalArgumentException("판매자 주문이 전체 주문에 속하지 않습니다.");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
