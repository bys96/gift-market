export type NotificationContext = "BUYER" | "SELLER" | "ADMIN";

export type NotificationType =
  | "ORDER_SHIPPED"
  | "ORDER_CANCELLED_BY_SELLER"
  | "CANCELLATION_COMPLETED"
  | "RETURN_APPROVED"
  | "RETURN_REJECTED"
  | "RETURN_COMPLETED"
  | "EXCHANGE_APPROVED"
  | "EXCHANGE_REJECTED"
  | "EXCHANGE_RESHIPPED"
  | "EXCHANGE_COMPLETED"
  | "PRODUCT_INQUIRY_ANSWERED"
  | "NEW_ORDER"
  | "CANCELLATION_REQUESTED"
  | "RETURN_REQUESTED"
  | "EXCHANGE_REQUESTED"
  | "PRODUCT_INQUIRY_CREATED"
  | "SELLER_APPLICATION_CREATED";

export interface Notification {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  targetUrl: string | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationPage {
  content: Notification[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface NotificationUnreadCount {
  unreadCount: number;
}
