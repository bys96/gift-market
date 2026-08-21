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
        name = "return_requests",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_return_requests_client_request_key",
                        columnNames = "client_request_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_return_requests_order_status",
                        columnList = "order_id, status"
                ),
                @Index(
                        name = "idx_return_requests_seller_order_status",
                        columnList = "seller_order_id, status"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_return_requests_order")
    )
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "seller_order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_return_requests_seller_order")
    )
    private SellerOrder sellerOrder;

    @Column(
            name = "client_request_key",
            nullable = false,
            length = 100
    )
    private String clientRequestKey;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "reason_type",
            nullable = false,
            length = 30
    )
    private ReturnReasonType reasonType;

    @Column(
            name = "reason",
            nullable = false,
            length = 500
    )
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "responsibility",
            length = 20
    )
    private ReturnResponsibility responsibility;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private ReturnRequestStatus status;

    @Column(
            name = "collection_recipient_name",
            nullable = false,
            length = 100
    )
    private String collectionRecipientName;

    @Column(
            name = "collection_phone",
            nullable = false,
            length = 30
    )
    private String collectionPhone;

    @Column(
            name = "collection_postal_code",
            nullable = false,
            length = 20
    )
    private String collectionPostalCode;

    @Column(
            name = "collection_address",
            nullable = false,
            length = 255
    )
    private String collectionAddress;

    @Column(
            name = "collection_address_detail",
            length = 255
    )
    private String collectionAddressDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "collection_shipment_id",
            unique = true,
            foreignKey = @ForeignKey(name = "fk_return_requests_collection_shipment")
    )
    private Shipment collectionShipment;

    @Column(
            name = "requested_at",
            nullable = false
    )
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "collecting_at")
    private LocalDateTime collectingAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    @Column(name = "refunding_at")
    private LocalDateTime refundingAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(
            name = "rejected_reason",
            length = 500
    )
    private String rejectedReason;

    private ReturnRequest(
            Order order,
            SellerOrder sellerOrder,
            String clientRequestKey,
            ReturnReasonType reasonType,
            String reason,
            String collectionRecipientName,
            String collectionPhone,
            String collectionPostalCode,
            String collectionAddress,
            String collectionAddressDetail,
            LocalDateTime requestedAt
    ) {
        validateOrderConsistency(order, sellerOrder);

        this.order = order;
        this.sellerOrder = sellerOrder;
        this.clientRequestKey = requireText(
                clientRequestKey,
                "반품 요청 키가 필요합니다."
        );
        this.reasonType = requireReasonType(reasonType);
        this.reason = requireText(
                reason,
                "반품 사유가 필요합니다."
        );
        this.responsibility = resolveResponsibility(reasonType);
        this.status = ReturnRequestStatus.REQUESTED;

        this.collectionRecipientName = requireText(
                collectionRecipientName,
                "반품 회수 수령인 이름이 필요합니다."
        );
        this.collectionPhone = requireText(
                collectionPhone,
                "반품 회수 연락처가 필요합니다."
        );
        this.collectionPostalCode = requireText(
                collectionPostalCode,
                "반품 회수 우편번호가 필요합니다."
        );
        this.collectionAddress = requireText(
                collectionAddress,
                "반품 회수 주소가 필요합니다."
        );
        this.collectionAddressDetail = normalizeNullableText(
                collectionAddressDetail
        );

        if (requestedAt == null) {
            throw new IllegalArgumentException("반품 요청 시각이 필요합니다.");
        }

        this.requestedAt = requestedAt;
    }

    public static ReturnRequest createRequested(
            Order order,
            SellerOrder sellerOrder,
            String clientRequestKey,
            ReturnReasonType reasonType,
            String reason,
            String collectionRecipientName,
            String collectionPhone,
            String collectionPostalCode,
            String collectionAddress,
            String collectionAddressDetail,
            LocalDateTime requestedAt
    ) {
        return new ReturnRequest(
                order,
                sellerOrder,
                clientRequestKey,
                reasonType,
                reason,
                collectionRecipientName,
                collectionPhone,
                collectionPostalCode,
                collectionAddress,
                collectionAddressDetail,
                requestedAt
        );
    }

    public void confirmResponsibility(
            ReturnResponsibility responsibility
    ) {
        if (reasonType != ReturnReasonType.OTHER) {
            throw new IllegalStateException(
                    "기타 반품 사유만 귀책 주체를 별도로 확정할 수 있습니다."
            );
        }

        if (this.responsibility != null) {
            throw new IllegalStateException(
                    "이미 반품 귀책 주체가 확정되었습니다."
            );
        }

        if (responsibility == null) {
            throw new IllegalArgumentException(
                    "반품 귀책 주체가 필요합니다."
            );
        }

        this.responsibility = responsibility;
    }

    public void approve(LocalDateTime approvedAt) {
        requireStatus(ReturnRequestStatus.REQUESTED);

        if (responsibility == null) {
            throw new IllegalStateException(
                    "반품 귀책 주체가 확정되지 않았습니다."
            );
        }

        if (approvedAt == null) {
            throw new IllegalArgumentException(
                    "반품 승인 시각이 필요합니다."
            );
        }

        status = ReturnRequestStatus.APPROVED;
        this.approvedAt = approvedAt;
    }

    public void assignCollectionShipment(
            Shipment collectionShipment
    ) {
        if (status != ReturnRequestStatus.APPROVED) {
            throw new IllegalStateException(
                    "승인된 반품만 회수 배송을 등록할 수 있습니다."
            );
        }

        if (collectionShipment == null) {
            throw new IllegalArgumentException(
                    "반품 회수 배송이 필요합니다."
            );
        }

        if (collectionShipment.getSellerOrder() != sellerOrder) {
            throw new IllegalArgumentException(
                    "반품 요청과 회수 배송의 판매자 주문이 일치하지 않습니다."
            );
        }

        if (collectionShipment.getType() != ShipmentType.RETURN_COLLECTION) {
            throw new IllegalArgumentException(
                    "반품 회수 배송만 연결할 수 있습니다."
            );
        }

        if (this.collectionShipment != null) {
            throw new IllegalStateException(
                    "이미 반품 회수 배송이 등록되었습니다."
            );
        }

        this.collectionShipment = collectionShipment;
    }

    public void startCollecting(LocalDateTime collectingAt) {
        requireStatus(ReturnRequestStatus.APPROVED);

        if (collectionShipment == null) {
            throw new IllegalStateException(
                    "반품 회수 배송이 등록되지 않았습니다."
            );
        }

        if (collectingAt == null) {
            throw new IllegalArgumentException(
                    "반품 회수 시작 시각이 필요합니다."
            );
        }

        status = ReturnRequestStatus.COLLECTING;
        this.collectingAt = collectingAt;
    }

    public void receive(LocalDateTime receivedAt) {
        requireStatus(ReturnRequestStatus.COLLECTING);

        if (receivedAt == null) {
            throw new IllegalArgumentException(
                    "반품 입고 시각이 필요합니다."
            );
        }

        status = ReturnRequestStatus.RECEIVED;
        this.receivedAt = receivedAt;
    }

    public void completeInspection(LocalDateTime inspectedAt) {
        requireStatus(ReturnRequestStatus.RECEIVED);

        if (inspectedAt == null) {
            throw new IllegalArgumentException(
                    "반품 검수 시각이 필요합니다."
            );
        }

        status = ReturnRequestStatus.INSPECTED;
        this.inspectedAt = inspectedAt;
    }

    public void startRefunding(LocalDateTime refundingAt) {
        requireStatus(ReturnRequestStatus.INSPECTED);

        if (refundingAt == null) {
            throw new IllegalArgumentException(
                    "반품 환불 시작 시각이 필요합니다."
            );
        }

        status = ReturnRequestStatus.REFUNDING;
        this.refundingAt = refundingAt;
    }

    public void complete(LocalDateTime completedAt) {
        requireStatus(ReturnRequestStatus.REFUNDING);

        if (completedAt == null) {
            throw new IllegalArgumentException(
                    "반품 완료 시각이 필요합니다."
            );
        }

        status = ReturnRequestStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public void reject(
            String rejectedReason,
            LocalDateTime rejectedAt
    ) {
        requireStatus(ReturnRequestStatus.REQUESTED);

        if (rejectedAt == null) {
            throw new IllegalArgumentException(
                    "반품 거절 시각이 필요합니다."
            );
        }

        status = ReturnRequestStatus.REJECTED;
        this.rejectedReason = requireText(
                rejectedReason,
                "반품 거절 사유가 필요합니다."
        );
        this.rejectedAt = rejectedAt;
    }

    public void cancel(LocalDateTime canceledAt) {
        if (status != ReturnRequestStatus.REQUESTED
                && status != ReturnRequestStatus.APPROVED) {
            throw new IllegalStateException(
                    status + " 상태에서는 반품을 철회할 수 없습니다."
            );
        }

        if (canceledAt == null) {
            throw new IllegalArgumentException(
                    "반품 철회 시각이 필요합니다."
            );
        }

        status = ReturnRequestStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    public void fail(LocalDateTime failedAt) {
        requireStatus(ReturnRequestStatus.REFUNDING);

        if (failedAt == null) {
            throw new IllegalArgumentException(
                    "반품 실패 시각이 필요합니다."
            );
        }

        status = ReturnRequestStatus.FAILED;
        this.failedAt = failedAt;
    }

    private void requireStatus(
            ReturnRequestStatus expected
    ) {
        if (status != expected) {
            throw new IllegalStateException(
                    status + " 상태에서는 해당 반품 상태 전이를 수행할 수 없습니다."
            );
        }
    }

    private static ReturnResponsibility resolveResponsibility(
            ReturnReasonType reasonType
    ) {
        return switch (reasonType) {
            case CHANGE_OF_MIND,
                 OPTION_MISTAKE -> ReturnResponsibility.BUYER;

            case DEFECTIVE,
                 WRONG_ITEM,
                 DAMAGED,
                 DESCRIPTION_MISMATCH -> ReturnResponsibility.SELLER;

            case OTHER -> null;
        };
    }

    private static ReturnReasonType requireReasonType(
            ReturnReasonType reasonType
    ) {
        if (reasonType == null) {
            throw new IllegalArgumentException(
                    "반품 사유 유형이 필요합니다."
            );
        }
        return reasonType;
    }

    private static void validateOrderConsistency(
            Order order,
            SellerOrder sellerOrder
    ) {
        if (order == null
                || sellerOrder == null
                || sellerOrder.getOrder() != order) {
            throw new IllegalArgumentException(
                    "판매자 주문이 전체 주문에 속하지 않습니다."
            );
        }
    }

    private static String requireText(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeNullableText(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}