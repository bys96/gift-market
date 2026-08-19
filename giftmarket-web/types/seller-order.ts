export type SellerOrderStatus =
  | "PENDING_PAYMENT"
  | "PAID"
  | "PREPARING"
  | "SHIPPED"
  | "DELIVERED"
  | "CANCELLED";

export const SELLER_ORDER_STATUS_LABEL: Record<SellerOrderStatus, string> = {
  PENDING_PAYMENT: "결제대기",
  PAID: "결제완료",
  PREPARING: "상품준비중",
  SHIPPED: "배송중",
  DELIVERED: "배송완료",
  CANCELLED: "취소",
};

export interface SellerOrderListItem {
  sellerOrderId: number;
  orderId: number;
  merchantOrderId: string;
  status: SellerOrderStatus;
  orderedAt: string | null;
  representativeProductName: string;
  productTypeCount: number;
  totalQuantity: number;
  totalProductAmount: number;
  recipientName: string;
  shippingCompany: string | null;
  trackingNumber: string | null;
}

export interface SellerOrderPage {
  orders: SellerOrderListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface SellerOrderItem {
  orderItemId: number;
  productId: number;
  variantId: number | null;
  productName: string;
  brandName: string | null;
  optionSnapshot: string | null;
  representativeImageKey: string | null;
  productPrice: number;
  additionalPrice: number;
  unitPrice: number;
  quantity: number;
  canceledQuantity: number;
  remainingQuantity: number;
  totalPrice: number;
  freeShipping: boolean;
  shippingFee: number;
}

export type SellerOrderCancellationStatus =
  | "REQUESTED"
  | "PROCESSING"
  | "COMPLETED"
  | "REJECTED"
  | "FAILED";

export interface SellerOrderCancellationSummary {
  cancellationId: number;
  status: SellerOrderCancellationStatus;
  requestedAt: string;
}

export interface SellerOrderDetail {
  sellerOrderId: number;
  orderId: number;
  merchantOrderId: string;
  status: SellerOrderStatus;
  orderedAt: string | null;
  items: SellerOrderItem[];
  recipientName: string;
  recipientPhone: string;
  postalCode: string;
  address: string;
  addressDetail: string | null;
  shippingCompany: string | null;
  trackingNumber: string | null;
  preparedAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  cancellations: SellerOrderCancellationSummary[];
}

export interface SellerOrderShipRequest {
  shippingCompany: string;
  trackingNumber: string;
}
