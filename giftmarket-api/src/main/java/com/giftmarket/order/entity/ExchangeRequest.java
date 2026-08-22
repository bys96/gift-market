package com.giftmarket.order.entity;

import com.giftmarket.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "exchange_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exchange_requests_client_request_key",
                columnNames = "client_request_key"
        ),
        indexes = {
                @Index(name = "idx_exchange_requests_order_status", columnList = "order_id, status"),
                @Index(name = "idx_exchange_requests_seller_order_status", columnList = "seller_order_id, status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exchange_requests_order"))
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exchange_requests_seller_order"))
    private SellerOrder sellerOrder;

    @Column(name = "client_request_key", nullable = false, length = 100)
    private String clientRequestKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 30)
    private ExchangeReasonType reasonType;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExchangeResponsibility responsibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExchangeRequestStatus status;

    @Column(name = "collection_recipient_name", nullable = false, length = 100)
    private String collectionRecipientName;
    @Column(name = "collection_phone", nullable = false, length = 30)
    private String collectionPhone;
    @Column(name = "collection_postal_code", nullable = false, length = 20)
    private String collectionPostalCode;
    @Column(name = "collection_address", nullable = false, length = 255)
    private String collectionAddress;
    @Column(name = "collection_address_detail", length = 255)
    private String collectionAddressDetail;

    @Column(name = "reshipping_recipient_name", nullable = false, length = 100)
    private String reshippingRecipientName;
    @Column(name = "reshipping_phone", nullable = false, length = 30)
    private String reshippingPhone;
    @Column(name = "reshipping_postal_code", nullable = false, length = 20)
    private String reshippingPostalCode;
    @Column(name = "reshipping_address", nullable = false, length = 255)
    private String reshippingAddress;
    @Column(name = "reshipping_address_detail", length = 255)
    private String reshippingAddressDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_shipment_id", unique = true,
            foreignKey = @ForeignKey(name = "fk_exchange_requests_collection_shipment"))
    private Shipment collectionShipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outbound_shipment_id", unique = true,
            foreignKey = @ForeignKey(name = "fk_exchange_requests_outbound_shipment"))
    private Shipment outboundShipment;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    @Column(name = "payment_pending_at")
    private LocalDateTime paymentPendingAt;
    @Column(name = "payment_due_at")
    private LocalDateTime paymentDueAt;
    @Column(name = "collecting_at")
    private LocalDateTime collectingAt;
    @Column(name = "received_at")
    private LocalDateTime receivedAt;
    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;
    @Column(name = "reshipping_at")
    private LocalDateTime reshippingAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;

    private ExchangeRequest(
            Order order, SellerOrder sellerOrder, String clientRequestKey,
            ExchangeReasonType reasonType, String reason,
            String collectionRecipientName, String collectionPhone,
            String collectionPostalCode, String collectionAddress, String collectionAddressDetail,
            String reshippingRecipientName, String reshippingPhone,
            String reshippingPostalCode, String reshippingAddress, String reshippingAddressDetail,
            LocalDateTime requestedAt
    ) {
        validateOrderConsistency(order, sellerOrder);
        if (reasonType == null) throw new IllegalArgumentException("교환 사유 유형이 필요합니다.");
        if (requestedAt == null) throw new IllegalArgumentException("교환 요청 시각이 필요합니다.");
        this.order = order;
        this.sellerOrder = sellerOrder;
        this.clientRequestKey = requireText(clientRequestKey, "교환 요청 키가 필요합니다.");
        this.reasonType = reasonType;
        this.reason = requireText(reason, "교환 사유가 필요합니다.");
        this.responsibility = reasonType.defaultResponsibility();
        this.status = ExchangeRequestStatus.REQUESTED;
        this.collectionRecipientName = requireText(collectionRecipientName, "교환 회수 수령인이 필요합니다.");
        this.collectionPhone = requireText(collectionPhone, "교환 회수 연락처가 필요합니다.");
        this.collectionPostalCode = requireText(collectionPostalCode, "교환 회수 우편번호가 필요합니다.");
        this.collectionAddress = requireText(collectionAddress, "교환 회수 주소가 필요합니다.");
        this.collectionAddressDetail = nullableText(collectionAddressDetail);
        this.reshippingRecipientName = requireText(reshippingRecipientName, "교환 재배송 수령인이 필요합니다.");
        this.reshippingPhone = requireText(reshippingPhone, "교환 재배송 연락처가 필요합니다.");
        this.reshippingPostalCode = requireText(reshippingPostalCode, "교환 재배송 우편번호가 필요합니다.");
        this.reshippingAddress = requireText(reshippingAddress, "교환 재배송 주소가 필요합니다.");
        this.reshippingAddressDetail = nullableText(reshippingAddressDetail);
        this.requestedAt = requestedAt;
    }

    public static ExchangeRequest createRequested(
            Order order, SellerOrder sellerOrder, String clientRequestKey,
            ExchangeReasonType reasonType, String reason,
            String collectionRecipientName, String collectionPhone,
            String collectionPostalCode, String collectionAddress, String collectionAddressDetail,
            String reshippingRecipientName, String reshippingPhone,
            String reshippingPostalCode, String reshippingAddress, String reshippingAddressDetail,
            LocalDateTime requestedAt
    ) {
        return new ExchangeRequest(order, sellerOrder, clientRequestKey, reasonType, reason,
                collectionRecipientName, collectionPhone, collectionPostalCode,
                collectionAddress, collectionAddressDetail, reshippingRecipientName,
                reshippingPhone, reshippingPostalCode, reshippingAddress,
                reshippingAddressDetail, requestedAt);
    }

    public void confirmResponsibility(ExchangeResponsibility responsibility) {
        if (reasonType != ExchangeReasonType.OTHER) {
            throw new IllegalStateException("기타 교환 사유만 귀책을 별도로 확정할 수 있습니다.");
        }
        if (this.responsibility != null) throw new IllegalStateException("교환 귀책이 이미 확정되었습니다.");
        if (responsibility == null) throw new IllegalArgumentException("교환 귀책이 필요합니다.");
        this.responsibility = responsibility;
    }

    public void approve(LocalDateTime approvedAt) {
        requireStatus(ExchangeRequestStatus.REQUESTED);
        if (responsibility == null) throw new IllegalStateException("교환 귀책이 확정되지 않았습니다.");
        if (approvedAt == null) throw new IllegalArgumentException("교환 승인 시각이 필요합니다.");
        status = ExchangeRequestStatus.APPROVED;
        this.approvedAt = approvedAt;
    }

    public void startPaymentPending(LocalDateTime paymentPendingAt, LocalDateTime paymentDueAt) {
        requireStatus(ExchangeRequestStatus.APPROVED);
        if (responsibility != ExchangeResponsibility.BUYER) {
            throw new IllegalStateException("구매자 귀책 교환만 배송비 결제를 대기할 수 있습니다.");
        }
        if (paymentPendingAt == null || paymentDueAt == null || !paymentDueAt.isAfter(paymentPendingAt)) {
            throw new IllegalArgumentException("교환 배송비 결제 기한을 확인해주세요.");
        }
        status = ExchangeRequestStatus.PAYMENT_PENDING;
        this.paymentPendingAt = paymentPendingAt;
        this.paymentDueAt = paymentDueAt;
    }

    public void assignCollectionShipment(Shipment shipment) {
        if (status != ExchangeRequestStatus.APPROVED && status != ExchangeRequestStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("승인되거나 결제 대기 중인 교환만 회수 배송을 연결할 수 있습니다.");
        }
        validateShipment(shipment, ShipmentType.EXCHANGE_COLLECTION);
        if (collectionShipment != null) throw new IllegalStateException("교환 회수 배송이 이미 연결되었습니다.");
        collectionShipment = shipment;
    }

    public void startCollecting(LocalDateTime collectingAt) {
        ExchangeRequestStatus expected = responsibility == ExchangeResponsibility.BUYER
                ? ExchangeRequestStatus.PAYMENT_PENDING : ExchangeRequestStatus.APPROVED;
        requireStatus(expected);
        if (collectionShipment == null) throw new IllegalStateException("교환 회수 배송이 필요합니다.");
        if (collectingAt == null) throw new IllegalArgumentException("교환 회수 시작 시각이 필요합니다.");
        status = ExchangeRequestStatus.COLLECTING;
        this.collectingAt = collectingAt;
    }

    public void receive(LocalDateTime receivedAt) {
        requireStatus(ExchangeRequestStatus.COLLECTING);
        if (receivedAt == null) throw new IllegalArgumentException("교환 입고 시각이 필요합니다.");
        status = ExchangeRequestStatus.RECEIVED;
        this.receivedAt = receivedAt;
    }

    public void completeInspection(LocalDateTime inspectedAt) {
        requireStatus(ExchangeRequestStatus.RECEIVED);
        if (inspectedAt == null) throw new IllegalArgumentException("교환 검수 시각이 필요합니다.");
        status = ExchangeRequestStatus.INSPECTED;
        this.inspectedAt = inspectedAt;
    }

    public void assignOutboundShipment(Shipment shipment) {
        requireStatus(ExchangeRequestStatus.INSPECTED);
        validateShipment(shipment, ShipmentType.EXCHANGE_OUTBOUND);
        if (outboundShipment != null) throw new IllegalStateException("교환 재배송이 이미 연결되었습니다.");
        outboundShipment = shipment;
    }

    public void startReshipping(LocalDateTime reshippingAt) {
        requireStatus(ExchangeRequestStatus.INSPECTED);
        if (outboundShipment == null) throw new IllegalStateException("교환 재배송이 필요합니다.");
        if (reshippingAt == null) throw new IllegalArgumentException("교환 재배송 시작 시각이 필요합니다.");
        status = ExchangeRequestStatus.RESHIPPING;
        this.reshippingAt = reshippingAt;
    }

    public void complete(LocalDateTime completedAt) {
        requireStatus(ExchangeRequestStatus.RESHIPPING);
        if (outboundShipment == null || outboundShipment.getStatus() != ShipmentStatus.DELIVERED) {
            throw new IllegalStateException("교환 재배송 완료가 확인되지 않았습니다.");
        }
        if (completedAt == null) throw new IllegalArgumentException("교환 완료 시각이 필요합니다.");
        status = ExchangeRequestStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public void reject(String reason, LocalDateTime rejectedAt) {
        requireStatus(ExchangeRequestStatus.REQUESTED);
        if (rejectedAt == null) throw new IllegalArgumentException("교환 거절 시각이 필요합니다.");
        rejectedReason = requireText(reason, "교환 거절 사유가 필요합니다.");
        status = ExchangeRequestStatus.REJECTED;
        this.rejectedAt = rejectedAt;
    }

    public void cancel(LocalDateTime canceledAt) {
        if (status != ExchangeRequestStatus.REQUESTED && status != ExchangeRequestStatus.APPROVED
                && status != ExchangeRequestStatus.PAYMENT_PENDING) {
            throw new IllegalStateException(status + " 상태에서는 교환을 취소할 수 없습니다.");
        }
        if (canceledAt == null) throw new IllegalArgumentException("교환 취소 시각이 필요합니다.");
        status = ExchangeRequestStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    public void fail(LocalDateTime failedAt) {
        if (status == ExchangeRequestStatus.COMPLETED || status == ExchangeRequestStatus.REJECTED
                || status == ExchangeRequestStatus.CANCELED || status == ExchangeRequestStatus.FAILED) {
            throw new IllegalStateException(status + " 상태에서는 교환 실패 처리할 수 없습니다.");
        }
        if (failedAt == null) throw new IllegalArgumentException("교환 실패 시각이 필요합니다.");
        status = ExchangeRequestStatus.FAILED;
        this.failedAt = failedAt;
    }

    private void validateShipment(Shipment shipment, ShipmentType expectedType) {
        if (shipment == null) throw new IllegalArgumentException("교환 배송이 필요합니다.");
        if (shipment.getSellerOrder() != sellerOrder) {
            throw new IllegalArgumentException("교환 요청과 배송의 판매자 주문이 일치하지 않습니다.");
        }
        if (shipment.getType() != expectedType) {
            throw new IllegalArgumentException(expectedType + " 배송만 연결할 수 있습니다.");
        }
    }

    private void requireStatus(ExchangeRequestStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(status + " 상태에서는 해당 교환 상태 전이를 수행할 수 없습니다.");
        }
    }

    private static void validateOrderConsistency(Order order, SellerOrder sellerOrder) {
        if (order == null || sellerOrder == null || sellerOrder.getOrder() != order) {
            throw new IllegalArgumentException("판매자 주문이 전체 주문에 속하지 않습니다.");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String nullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
