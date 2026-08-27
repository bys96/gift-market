export type OrderStatus =
  | "ORDERED"
  | "PENDING_PAYMENT"
  | "PAID"
  | "PAYMENT_FAILED"
  | "PAYMENT_EXPIRED"
  | "CANCELLED";

export type BuyerOrderDeliveryStatus =
  | "PAYMENT_PENDING"
  | "PAYMENT_FAILED"
  | "PAYMENT_EXPIRED"
  | "PAID"
  | "PREPARING"
  | "SHIPPING"
  | "DELIVERED"
  | "CANCELLED";

export type BuyerSellerOrderStatus =
  | "PENDING_PAYMENT"
  | "PAID"
  | "PREPARING"
  | "SHIPPED"
  | "DELIVERED"
  | "CANCELLED";

export type PaymentStatus =
  | "READY"
  | "CONFIRMING"
  | "PAID"
  | "FAILED"
  | "EXPIRED"
  | "CANCELING"
  | "CANCELED";

export type OrderCancellationStatus =
  | "REQUESTED"
  | "PROCESSING"
  | "COMPLETED"
  | "REJECTED"
  | "FAILED";

export interface OrderCancellationItem {
  orderItemId: number;
  requestedQuantity: number;
}

export interface OrderCancellation {
  cancellationId: number;
  orderId: number;
  sellerOrderId: number;
  status: OrderCancellationStatus;
  reason: string;
  requestedAt: string;
  processingAt: string | null;
  completedAt: string | null;
  rejectedAt: string | null;
  rejectedReason: string | null;
  failedAt: string | null;
  items: OrderCancellationItem[];
}

export interface OrderCancellationCreateRequest {
  clientRequestKey: string;
  sellerOrderId: number;
  reason: string;
  items: Array<{ orderItemId: number; quantity: number }>;
}

export interface OrderHistoryItem {
  id: number;
  productId: number;
  variantId: number | null;

  productName: string;
  brandName: string | null;
  optionSnapshot: string | null;
  representativeImageKey: string | null;

  quantity: number;
  canceledQuantity: number;
  availableCancellationQuantity: number;
  confirmedQuantity: number;
  confirmableQuantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  deliveryStatus: BuyerOrderDeliveryStatus;
  orderedAt: string | null;

  totalProductAmount: number;
  totalShippingFee: number;
  totalAmount: number;
  items: OrderHistoryItem[];
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  deliveryStatus: BuyerOrderDeliveryStatus;

  totalProductAmount: number;
  totalShippingFee: number;
  totalAmount: number;
  refundedAmount: number;
  remainingPaymentAmount: number;

  recipientName: string;
  recipientPhone: string;
  postalCode: string;
  address: string;
  addressDetail: string | null;

  orderedAt: string | null;
  cancelledAt: string | null;

  items: OrderHistoryItem[];
  sellerOrders: BuyerSellerOrder[];
}

export interface BuyerOrderPage {
  content: OrderSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface PurchaseConfirmation {
  orderItemId: number;
  confirmedQuantity: number;
  confirmableQuantity: number;
}

export interface BuyerSellerOrder {
  sellerOrderId: number;
  sellerName: string;
  status: BuyerSellerOrderStatus;
  shippingCompany: string | null;
  trackingNumber: string | null;
  preparedAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  items: OrderHistoryItem[];
}

export interface OrderCreateRequest {
  clientOrderRequestKey: string;
  cartItemIds: number[];

  recipientName: string;
  recipientPhone: string;
  postalCode: string;
  address: string;
  addressDetail: string | null;
}

export interface OrderCancelResponse {
  orderId: number;
  orderStatus: OrderStatus;
  paymentStatus: PaymentStatus | null;
  message: string;
}

export interface DirectOrderCreateRequest {
  clientOrderRequestKey: string;
  productId: number;
  variantId: number | null;
  quantity: number;
  recipientName: string;
  recipientPhone: string;
  postalCode: string;
  address: string;
  addressDetail: string | null;
}

export interface OrderProductItem {
  key: string;
  productName: string;
  brandName: string | null;
  storeName: string;
  representativeImageKey: string | null;
  optionText: string | null;
  quantity: number;
  price: number;
  freeShipping: boolean;
  shippingFee: number;
}

export interface OrderCreateResponse {
  orderId: number;
  orderNumber: string;
  status: OrderStatus;

  paymentId: number;
  merchantPaymentId: string;
  paymentStatus: PaymentStatus;
  orderName: string;
  amount: number;

  totalProductAmount: number;
  totalShippingFee: number;
  totalAmount: number;

  orderedAt: string | null;
  expiresAt: string;
}
