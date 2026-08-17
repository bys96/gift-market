import type {
  BuyerOrderDeliveryStatus,
  BuyerSellerOrderStatus,
  OrderStatus,
} from "@/types/order";

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  ORDERED: "주문 완료",
  PENDING_PAYMENT: "결제 대기",
  PAID: "결제 완료",
  PAYMENT_FAILED: "결제 실패",
  PAYMENT_EXPIRED: "결제 만료",
  CANCELLED: "주문 취소",
};

export const BUYER_DELIVERY_STATUS_LABELS: Record<
  BuyerOrderDeliveryStatus,
  string
> = {
  PAYMENT_PENDING: "결제 대기",
  PAYMENT_FAILED: "결제 실패",
  PAYMENT_EXPIRED: "결제 만료",
  PAID: "결제 완료",
  PREPARING: "상품준비중",
  SHIPPING: "배송중",
  DELIVERED: "배송완료",
  CANCELLED: "주문 취소",
};

export const BUYER_SELLER_ORDER_STATUS_LABELS: Record<
  BuyerSellerOrderStatus,
  string
> = {
  PENDING_PAYMENT: "결제 대기",
  PAID: "결제 완료",
  PREPARING: "상품준비중",
  SHIPPED: "배송중",
  DELIVERED: "배송완료",
  CANCELLED: "취소",
};
