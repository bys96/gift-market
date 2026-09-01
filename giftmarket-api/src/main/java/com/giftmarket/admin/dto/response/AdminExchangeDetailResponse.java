package com.giftmarket.admin.dto.response;

import com.giftmarket.order.entity.*;
import com.giftmarket.payment.entity.*;
import java.time.LocalDateTime;
import java.util.List;

public record AdminExchangeDetailResponse(Long exchangeId, ExchangeRequestStatus status, ExchangeReasonType reasonType,
        String reason, ExchangeResponsibility responsibility, String rejectedReason, LocalDateTime requestedAt,
        LocalDateTime approvedAt, LocalDateTime paymentPendingAt, LocalDateTime paymentDueAt, LocalDateTime collectingAt,
        LocalDateTime receivedAt, LocalDateTime inspectedAt, LocalDateTime reshippingAt, LocalDateTime completedAt,
        LocalDateTime rejectedAt, LocalDateTime canceledAt, LocalDateTime failedAt, OrderInfo order, Buyer buyer,
        SellerInfo seller, List<Item> items, ShipmentInfo collectionShipment, ShipmentInfo outboundShipment,
        ShippingPayment shippingPayment) {
    public record OrderInfo(Long orderId, String orderNumber, OrderStatus status, LocalDateTime orderedAt) {}
    public record Buyer(Long userId, String name, String email) {}
    public record SellerInfo(Long sellerOrderId, SellerOrderStatus status, Long sellerId, String storeName) {}
    public record Item(Long exchangeItemId, Long orderItemId, Long productId, String productName, int originalQuantity,
            int exchangeQuantity, int exchangedQuantity, String originalOptionSnapshot, Long originalVariantId,
            String originalSku, long originalUnitPrice, String targetOptionSnapshot, Long targetVariantId,
            String targetSku, long targetUnitPrice, boolean sameVariant, ExchangeInspectionResult inspectionResult,
            int restockedQuantity, int reservedQuantity, int releasedQuantity, int consumedQuantity) {
        public static Item from(ExchangeRequestItem exchangeItem) {
            var orderItem = exchangeItem.getOrderItem();
            var originalVariant = orderItem.getVariant();
            var targetVariant = exchangeItem.getTargetVariant();
            Long originalVariantId = originalVariant == null ? null : originalVariant.getId();
            Long targetVariantId = targetVariant == null ? null : targetVariant.getId();
            return new Item(exchangeItem.getId(), orderItem.getId(), orderItem.getProduct().getId(), orderItem.getProductName(),
                    orderItem.getQuantity(), exchangeItem.getQuantity(), orderItem.getExchangedQuantity(), orderItem.getOptionSnapshot(),
                    originalVariantId, originalVariant == null ? null : originalVariant.getSkuCode(), orderItem.getUnitPrice(),
                    exchangeItem.getTargetOptionSnapshot(), targetVariantId, targetVariant == null ? null : targetVariant.getSkuCode(),
                    exchangeItem.getTargetUnitPrice(), originalVariantId != null && originalVariantId.equals(targetVariantId),
                    exchangeItem.getInspectionResult(), exchangeItem.getRestockedQuantity(), exchangeItem.getReservedQuantity(),
                    exchangeItem.getReleasedQuantity(), exchangeItem.getConsumedQuantity());
        }
    }
    public record ShipmentInfo(Long shipmentId, ShipmentType type, ShipmentStatus status, String shippingCompany,
            String trackingNumber, LocalDateTime shippedAt, LocalDateTime deliveredAt) {
        public static ShipmentInfo from(Shipment shipment) { return shipment == null ? null : new ShipmentInfo(shipment.getId(),
                shipment.getType(), shipment.getStatus(), shipment.getShippingCompany(), shipment.getTrackingNumber(),
                shipment.getShippedAt(), shipment.getDeliveredAt()); }
    }
    public record ShippingPayment(Long id, long amount, ExchangeShippingPaymentStatus status, LocalDateTime requestedAt,
            LocalDateTime succeededAt, LocalDateTime failedAt, LocalDateTime expiredAt, String failureCode) {
        public static ShippingPayment from(ExchangeShippingPayment payment) { return payment == null ? null : new ShippingPayment(
                payment.getId(), payment.getAmount(), payment.getStatus(), payment.getRequestedAt(), payment.getSucceededAt(),
                payment.getFailedAt(), payment.getExpiredAt(), payment.getFailureCode()); }
    }
}
