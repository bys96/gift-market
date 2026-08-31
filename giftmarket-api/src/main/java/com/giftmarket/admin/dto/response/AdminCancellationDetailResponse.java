package com.giftmarket.admin.dto.response;
import com.giftmarket.order.entity.*;
import com.giftmarket.payment.entity.*;
import com.giftmarket.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;

public record AdminCancellationDetailResponse(Long cancellationId,OrderCancellationStatus status,boolean requiresSellerApproval,
 String reason,String rejectedReason,LocalDateTime requestedAt,LocalDateTime processingAt,LocalDateTime completedAt,
 LocalDateTime rejectedAt,LocalDateTime failedAt,OrderInfo order,Buyer buyer,SellerInfo seller,List<Item> items,
 PaymentInfo payment,PaymentCancellationInfo paymentCancellation){
 public record OrderInfo(Long orderId,String orderNumber,OrderStatus status,LocalDateTime orderedAt){}
 public record Buyer(Long userId,String name,String email){public static Buyer from(User u){return new Buyer(u.getId(),u.getName(),u.getEmail());}}
 public record SellerInfo(Long sellerOrderId,SellerOrderStatus sellerOrderStatus,Long sellerId,String storeName){}
 public record Item(Long cancellationItemId,Long orderItemId,Long productId,String productName,String optionSnapshot,Long unitPrice,int originalQuantity,int cancelQuantity,int canceledQuantity,Long shippingFee){
  public static Item from(OrderCancellationItem ci){var i=ci.getOrderItem();return new Item(ci.getId(),i.getId(),i.getProduct().getId(),i.getProductName(),i.getOptionSnapshot(),i.getUnitPrice(),i.getQuantity(),ci.getQuantity(),i.getCanceledQuantity(),i.getShippingFee());}
 }
 public record PaymentInfo(Long paymentId,PaymentStatus status,Long originalAmount,long succeededRefundAmount){}
 public record PaymentCancellationInfo(Long paymentCancellationId,PaymentCancellationType type,PaymentCancellationStatus status,Long amount,LocalDateTime requestedAt,LocalDateTime canceledAt,LocalDateTime failedAt,String failureCode){
  public static PaymentCancellationInfo from(PaymentCancellation p){return p==null?null:new PaymentCancellationInfo(p.getId(),p.getType(),p.getStatus(),p.getAmount(),p.getRequestedAt(),p.getCanceledAt(),p.getFailedAt(),p.getFailureCode());}
 }
}
