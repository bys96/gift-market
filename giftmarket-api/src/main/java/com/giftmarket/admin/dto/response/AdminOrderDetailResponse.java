package com.giftmarket.admin.dto.response;

import com.giftmarket.order.entity.*;
import com.giftmarket.payment.entity.*;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.user.entity.*;
import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderDetailResponse(
        Long orderId, String orderNumber, OrderStatus orderStatus, LocalDateTime orderedAt,
        Long totalProductAmount, Long totalShippingFee, Long totalAmount,
        Buyer buyer, Recipient recipient, PaymentInfo payment, List<SellerOrderInfo> sellerOrders,
        ClaimSummary claims, RefundSummary refund
) {
    public record Buyer(Long userId, String name, String email, UserRole role, UserStatus status) {
        public static Buyer from(User u) { return new Buyer(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getStatus()); }
    }
    public record Recipient(String name, String phone, String postalCode, String address, String detailAddress) {}
    public record PaymentInfo(Long paymentId, PaymentProvider provider, PaymentStatus status, Long amount, String currency,
                              PaymentMethod method, EasyPayProvider easyPayProvider, String providerStatus,
                              LocalDateTime requestedAt, LocalDateTime approvedAt, LocalDateTime cancelledAt) {
        public static PaymentInfo from(Payment p) { return p == null ? null : new PaymentInfo(p.getId(), p.getProvider(), p.getStatus(),
                p.getAmount(), p.getCurrency(), p.getMethod(), p.getEasyPayProvider(), p.getProviderStatus(),
                p.getRequestedAt(), p.getApprovedAt(), p.getCancelledAt()); }
    }
    public record SellerOrderInfo(Long sellerOrderId, Long sellerId, String storeName, SellerStatus sellerStatus,
                                  SellerOrderStatus status, String shippingCompany, String trackingNumber,
                                  LocalDateTime preparedAt, LocalDateTime shippedAt, LocalDateTime deliveredAt,
                                  List<Item> items, List<ShipmentInfo> shipments) {}
    public record Item(Long orderItemId, Long productId, String productName, String optionSnapshot, Long unitPrice,
                       Integer quantity, Long totalPrice, Long shippingFee, int canceledQuantity,
                       int returnedQuantity, int exchangedQuantity, int confirmedQuantity) {
        public static Item from(OrderItem i) { return new Item(i.getId(), i.getProduct().getId(), i.getProductName(),
                i.getOptionSnapshot(), i.getUnitPrice(), i.getQuantity(), i.getTotalPrice(), i.getShippingFee(),
                i.getCanceledQuantity(), i.getReturnedQuantity(), i.getExchangedQuantity(), i.getConfirmedQuantity()); }
    }
    public record ShipmentInfo(Long shipmentId, ShipmentType type, ShipmentStatus status, String shippingCompany,
                               String trackingNumber, LocalDateTime shippedAt, LocalDateTime deliveredAt) {
        public static ShipmentInfo from(Shipment s) { return new ShipmentInfo(s.getId(), s.getType(), s.getStatus(),
                s.getShippingCompany(), s.getTrackingNumber(), s.getShippedAt(), s.getDeliveredAt()); }
    }
    public record ClaimSummary(long cancellationCount, long returnCount, long exchangeCount) {}
    public record RefundSummary(long succeededCount, long succeededAmount) {}
}
