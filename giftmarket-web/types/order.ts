export type OrderStatus = "ORDERED" | "CANCELLED";

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
  orderedAt: string;

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

  orderedAt: string;
  cancelledAt: string | null;

  items: OrderHistoryItem[];
}

export interface OrderCreateRequest {
  cartItemIds: number[];

  recipientName: string;
  recipientPhone: string;
  postalCode: string;
  address: string;
  addressDetail: string | null;
}

export interface DirectOrderCreateRequest {
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

  totalProductAmount: number;
  totalShippingFee: number;
  totalAmount: number;

  orderedAt: string;
}
