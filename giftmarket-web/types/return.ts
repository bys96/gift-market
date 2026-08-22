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
