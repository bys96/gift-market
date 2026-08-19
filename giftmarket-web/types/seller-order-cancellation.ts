export type SellerOrderCancellationStatus =
  | "REQUESTED"
  | "PROCESSING"
  | "COMPLETED"
  | "REJECTED"
  | "FAILED";

export const SELLER_ORDER_CANCELLATION_STATUS_LABEL: Record<
  SellerOrderCancellationStatus,
  string
> = {
  REQUESTED: "승인 대기",
  PROCESSING: "환불 처리 중",
  COMPLETED: "취소 완료",
  REJECTED: "취소 거절",
  FAILED: "환불 처리 실패",
};

export interface SellerOrderCancellationItem {
  orderItemId: number;
  productName: string;
  optionSnapshot: string | null;
  orderedQuantity: number;
  canceledQuantity: number;
  requestedQuantity: number;
}

export interface SellerOrderCancellation {
  cancellationId: number;
  orderNumber: string;
  sellerOrderId: number;
  status: SellerOrderCancellationStatus;
  reason: string;
  rejectedReason: string | null;
  requestedAt: string;
  processingAt: string | null;
  rejectedAt: string | null;
  recipientName: string;
  items: SellerOrderCancellationItem[];
}

export interface SellerOrderCancellationPage {
  cancellations: SellerOrderCancellation[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
