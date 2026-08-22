export type ReturnRequestStatus =
  | "REQUESTED"
  | "APPROVED"
  | "COLLECTING"
  | "RECEIVED"
  | "INSPECTED"
  | "REFUNDING"
  | "COMPLETED"
  | "REJECTED"
  | "CANCELED"
  | "FAILED";

export type ReturnReasonType =
  | "CHANGE_OF_MIND"
  | "OPTION_MISTAKE"
  | "DEFECTIVE"
  | "WRONG_ITEM"
  | "DAMAGED"
  | "DESCRIPTION_MISMATCH"
  | "OTHER";

export type ReturnResponsibility = "BUYER" | "SELLER";
export type ReturnInspectionResult = "RESTOCKABLE" | "NON_RESTOCKABLE";
export type ReturnShipmentType = "RETURN_COLLECTION";
export type ReturnShipmentStatus = "READY" | "SHIPPED" | "DELIVERED" | "CANCELED";

export const RETURN_STATUS_LABELS: Record<ReturnRequestStatus, string> = {
  REQUESTED: "반품 요청",
  APPROVED: "반품 승인",
  COLLECTING: "상품 회수 중",
  RECEIVED: "판매자 입고 완료",
  INSPECTED: "검수 완료",
  REFUNDING: "환불 처리 중",
  COMPLETED: "반품 완료",
  REJECTED: "반품 거절",
  CANCELED: "반품 철회",
  FAILED: "반품 처리 실패",
};

export const RETURN_REASON_LABELS: Record<ReturnReasonType, string> = {
  CHANGE_OF_MIND: "단순 변심",
  OPTION_MISTAKE: "옵션 선택 실수",
  DEFECTIVE: "상품 불량",
  WRONG_ITEM: "오배송",
  DAMAGED: "배송 중 파손",
  DESCRIPTION_MISMATCH: "상품 설명과 다름",
  OTHER: "기타",
};

export const RETURN_RESPONSIBILITY_LABELS: Record<ReturnResponsibility, string> = {
  BUYER: "구매자 귀책",
  SELLER: "판매자 귀책",
};

export const RETURN_INSPECTION_LABELS: Record<ReturnInspectionResult, string> = {
  RESTOCKABLE: "재입고 가능",
  NON_RESTOCKABLE: "재입고 불가",
};

export const RETURN_SHIPMENT_STATUS_LABELS: Record<ReturnShipmentStatus, string> = {
  READY: "회수 준비",
  SHIPPED: "회수 중",
  DELIVERED: "회수 완료",
  CANCELED: "회수 취소",
};

export interface ReturnRequestItem {
  orderItemId: number;
  productName: string;
  optionSnapshot: string | null;
  quantity: number;
  returnedQuantity: number;
  inspectionResult: ReturnInspectionResult | null;
  restockedQuantity: number;
}

export interface ReturnCollectionShipment {
  shipmentId: number;
  type: ReturnShipmentType;
  status: ReturnShipmentStatus;
  shippingCompany: string | null;
  trackingNumber: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
}

export interface ReturnRequest {
  returnRequestId: number;
  orderId: number;
  sellerOrderId: number;
  status: ReturnRequestStatus;
  reasonType: ReturnReasonType;
  reason: string;
  responsibility: ReturnResponsibility | null;
  collectionRecipientName: string;
  collectionPhone: string;
  collectionPostalCode: string;
  collectionAddress: string;
  collectionAddressDetail: string | null;
  requestedAt: string;
  approvedAt: string | null;
  collectingAt: string | null;
  receivedAt: string | null;
  inspectedAt: string | null;
  refundingAt: string | null;
  completedAt: string | null;
  rejectedAt: string | null;
  rejectedReason: string | null;
  canceledAt: string | null;
  failedAt: string | null;
  productRefundAmount: number | null;
  originalShippingRefundAmount: number | null;
  returnShippingCharge: number | null;
  refundAmount: number | null;
  collectionShipment: ReturnCollectionShipment | null;
  items: ReturnRequestItem[];
}

export interface ReturnRequestCreateRequest {
  clientRequestKey: string;
  reasonType: ReturnReasonType;
  reason: string;
  collectionRecipientName: string;
  collectionPhone: string;
  collectionPostalCode: string;
  collectionAddress: string;
  collectionAddressDetail: string | null;
  items: Array<{ orderItemId: number; quantity: number }>;
}

export interface SellerReturnRequestPage {
  returns: ReturnRequest[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface SellerReturnInspectRequest {
  items: Array<{
    orderItemId: number;
    inspectionResult: ReturnInspectionResult;
  }>;
}
