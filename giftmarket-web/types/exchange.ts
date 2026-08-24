export type ExchangeRequestStatus =
  | "REQUESTED" | "APPROVED" | "PAYMENT_PENDING" | "COLLECTING"
  | "RECEIVED" | "INSPECTED" | "RESHIPPING" | "COMPLETED"
  | "REJECTED" | "CANCELED" | "FAILED";

export type ExchangeReasonType =
  | "CHANGE_OF_MIND" | "OPTION_MISTAKE" | "DEFECTIVE" | "WRONG_ITEM"
  | "DAMAGED" | "DESCRIPTION_MISMATCH" | "OTHER";
export type ExchangeResponsibility = "BUYER" | "SELLER";
export type ExchangeInspectionResult = "RESTOCKABLE" | "NON_RESTOCKABLE";
export type ExchangeShipmentStatus = "READY" | "SHIPPED" | "DELIVERED" | "CANCELED";
export type ExchangeShippingPaymentStatus =
  | "READY" | "REQUESTED" | "SUCCEEDED" | "FAILED" | "EXPIRED" | "COMPENSATION_REQUIRED";

export const EXCHANGE_STATUS_LABELS: Record<ExchangeRequestStatus, string> = {
  REQUESTED: "교환 요청", APPROVED: "교환 승인", PAYMENT_PENDING: "교환배송비 결제 대기",
  COLLECTING: "교환 상품 회수 중", RECEIVED: "판매자 입고 완료", INSPECTED: "검수 완료",
  RESHIPPING: "교환품 배송 중", COMPLETED: "교환 완료", REJECTED: "교환 거절",
  CANCELED: "교환 취소", FAILED: "교환 처리 실패",
};
export const EXCHANGE_REASON_LABELS: Record<ExchangeReasonType, string> = {
  CHANGE_OF_MIND: "단순 변심", OPTION_MISTAKE: "옵션 선택 실수", DEFECTIVE: "상품 불량",
  WRONG_ITEM: "오배송", DAMAGED: "배송 중 파손", DESCRIPTION_MISMATCH: "상품 설명과 다름", OTHER: "기타",
};
export const EXCHANGE_RESPONSIBILITY_LABELS: Record<ExchangeResponsibility, string> = {
  BUYER: "구매자 귀책", SELLER: "판매자 귀책",
};
export const EXCHANGE_INSPECTION_LABELS: Record<ExchangeInspectionResult, string> = {
  RESTOCKABLE: "재입고 가능", NON_RESTOCKABLE: "재입고 불가",
};
export const EXCHANGE_SHIPMENT_STATUS_LABELS: Record<ExchangeShipmentStatus, string> = {
  READY: "준비 중", SHIPPED: "배송 중", DELIVERED: "배송 완료", CANCELED: "취소",
};
export const EXCHANGE_PAYMENT_STATUS_LABELS: Record<ExchangeShippingPaymentStatus, string> = {
  READY: "결제 대기", REQUESTED: "결제 결과 확인 중", SUCCEEDED: "결제 완료",
  FAILED: "결제 미완료", EXPIRED: "결제기한 만료", COMPENSATION_REQUIRED: "결제 상태 확인 필요",
};

export interface ExchangeRequestItem {
  orderItemId: number; originalProductName: string; originalOptionSnapshot: string | null;
  quantity: number; targetProductName: string; targetOptionSnapshot: string | null;
  targetUnitPrice: number; inspectionResult: ExchangeInspectionResult | null; restockedQuantity: number;
}
export interface ExchangeRequestImage { imageId: number; url: string; sortOrder: number; }
export interface ExchangeShipment {
  shipmentId: number; type: "EXCHANGE_COLLECTION" | "EXCHANGE_OUTBOUND";
  status: ExchangeShipmentStatus; shippingCompany: string | null; trackingNumber: string | null;
  shippedAt: string | null; deliveredAt: string | null;
}
export interface ExchangeRequest {
  exchangeRequestId: number; orderId: number; sellerOrderId: number; status: ExchangeRequestStatus;
  reasonType: ExchangeReasonType; reason: string; responsibility: ExchangeResponsibility | null;
  collectionRecipientName: string; collectionPhone: string; collectionPostalCode: string;
  collectionAddress: string; collectionAddressDetail: string | null;
  reshippingRecipientName: string; reshippingPhone: string; reshippingPostalCode: string;
  reshippingAddress: string; reshippingAddressDetail: string | null;
  requestedAt: string; approvedAt: string | null; paymentPendingAt: string | null; paymentDueAt: string | null;
  collectingAt: string | null; receivedAt: string | null; inspectedAt: string | null;
  reshippingAt: string | null; completedAt: string | null; rejectedAt: string | null;
  rejectedReason: string | null; canceledAt: string | null; failedAt: string | null;
  collectionShipment: ExchangeShipment | null; outboundShipment: ExchangeShipment | null;
  items: ExchangeRequestItem[]; images: ExchangeRequestImage[];
}
export interface ExchangeRequestCreateRequest {
  clientRequestKey: string; reasonType: ExchangeReasonType; reason: string;
  collectionRecipientName: string; collectionPhone: string; collectionPostalCode: string;
  collectionAddress: string; collectionAddressDetail: string | null;
  reshippingRecipientName: string; reshippingPhone: string; reshippingPostalCode: string;
  reshippingAddress: string; reshippingAddressDetail: string | null;
  items: Array<{ orderItemId: number; quantity: number; targetVariantId: number | null }>;
  imageObjectKeys: string[];
}
export interface ExchangeShippingPayment {
  paymentId: number; exchangeRequestId: number; status: ExchangeShippingPaymentStatus;
  amount: number; providerOrderId: string; idempotencyKey: string;
  paymentDueAt: string | null; userMessage: string;
}
