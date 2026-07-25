export interface OrderHistoryItem {
  id: number;
  productId: number;
  productName: string;
  productImageUrl: string;
  quantity: number;
  price: number;
}

export type OrderStatus =
  | "PAYMENT_COMPLETED"
  | "PREPARING"
  | "SHIPPING"
  | "DELIVERED"
  | "CANCELED";

export interface OrderSummary {
  id: number;
  orderNumber: string;
  orderedAt: string;
  status: OrderStatus;
  totalPrice: number;
  items: OrderHistoryItem[];
}

export interface OrderDetailItem {
  id: number;
  productId: number;
  productName: string;
  productImageUrl: string;
  quantity: number;
  price: number;
}

export interface OrderCustomer {
  name: string;
  email: string;
  phoneNumber: string;
}

export interface OrderRecipient {
  name: string;
  phoneNumber: string;
  zipCode: string;
  address: string;
  addressDetail: string;
  deliveryMessage: string;
}

export interface OrderPayment {
  productAmount: number;
  deliveryFee: number;
  discountAmount: number;
  totalAmount: number;
  paymentMethod: string;
  paidAt: string;
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  orderedAt: string;
  status: OrderStatus;
  items: OrderDetailItem[];
  customer: OrderCustomer;
  recipient: OrderRecipient;
  payment: OrderPayment;
}
