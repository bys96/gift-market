package com.giftmarket.admin.dto.response;
import com.giftmarket.order.entity.*;
import com.giftmarket.payment.entity.*;
import java.time.LocalDateTime;

public record AdminCancellationSummaryResponse(Long cancellationId, OrderCancellationStatus status,
 boolean requiresSellerApproval, LocalDateTime requestedAt, LocalDateTime completedAt, LocalDateTime rejectedAt,
 LocalDateTime failedAt, Long orderId, String orderNumber, Long userId, String userName, String userEmail,
 Long sellerOrderId, Long sellerId, String storeName, String representativeProductName, long productTypeCount,
 long requestedQuantity, Long refundAmount, PaymentCancellationStatus refundStatus) {
 public static AdminCancellationSummaryResponse from(OrderCancellation c, String product, long types, long quantity, PaymentCancellation pc) {
  var o=c.getOrder();var u=o.getUser();var so=c.getSellerOrder();var s=so.getSeller();
  return new AdminCancellationSummaryResponse(c.getId(),c.getStatus(),c.isRequiresSellerApproval(),c.getRequestedAt(),c.getCompletedAt(),c.getRejectedAt(),c.getFailedAt(),o.getId(),o.getOrderNumber(),u.getId(),u.getName(),u.getEmail(),so.getId(),s.getId(),s.getStoreName(),product,types,quantity,pc==null?null:pc.getAmount(),pc==null?null:pc.getStatus());
 }
}
