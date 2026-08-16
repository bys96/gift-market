export type OrderStatus =
  | "ORDERED"
  | "PENDING_PAYMENT"
  | "PAID"
  | "PAYMENT_FAILED"
  | "PAYMENT_EXPIRED"
  | "CANCELLED";

export type PaymentStatus =
  | "READY"
  | "CONFIRMING"
  | "PAID"
  | "FAILED"
  | "EXPIRED"
  | "CANCELING"
  | "CANCELED";

export interface OrderHistoryItem {
  id: number;
  productId: number;
  variantId: number | null;

  productName: string;
  brandName: string | null;
  optionSnapshot: string | null;
  representativeImageKey: string | null;

  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: OrderStatus;
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

  totalProductAmount: number;
  totalShippingFee: number;
  totalAmount: number;

  recipientName: string;
  recipientPhone: string;
  postalCode: string;
  address: string;
  addressDetail: string | null;

  orderedAt: string | null;
  cancelledAt: string | null;

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
