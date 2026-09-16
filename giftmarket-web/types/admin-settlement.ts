export type AdminSettlementStatus = "READY" | "ON_HOLD" | "CONFIRMED";
export type AdminSettlementLedgerType =
  | "SALE_PRODUCT" | "SALE_SHIPPING" | "CANCELLATION_REFUND"
  | "RETURN_REFUND" | "COMMISSION" | "COMMISSION_REVERSAL" | "MANUAL_ADJUSTMENT";
export type AdminSettlementSourceType =
  | "SELLER_ORDER" | "PAYMENT_CANCELLATION" | "ADMIN_ADJUSTMENT" | "LEDGER_ENTRY";

export const ADMIN_SETTLEMENT_STATUS_LABEL: Record<AdminSettlementStatus, string> = {
  READY: "정산 대기",
  ON_HOLD: "정산 보류",
  CONFIRMED: "정산 확정",
};

export const ADMIN_SETTLEMENT_LEDGER_LABEL: Record<AdminSettlementLedgerType, string> = {
  SALE_PRODUCT: "상품 매출",
  SALE_SHIPPING: "배송비 매출",
  CANCELLATION_REFUND: "취소 환불",
  RETURN_REFUND: "반품 환불",
  COMMISSION: "판매 수수료",
  COMMISSION_REVERSAL: "수수료 환입",
  MANUAL_ADJUSTMENT: "정산 조정",
};

export const ADMIN_SETTLEMENT_SOURCE_LABEL: Record<AdminSettlementSourceType, string> = {
  SELLER_ORDER: "판매자 주문",
  PAYMENT_CANCELLATION: "결제 취소",
  ADMIN_ADJUSTMENT: "관리자 조정",
  LEDGER_ENTRY: "원장 정정",
};

export interface AdminSettlement {
  settlementId: number;
  settlementNumber: string;
  sellerId: number;
  storeName: string;
  periodStart: string;
  periodEnd: string;
  currency: string;
  productSalesAmount: number;
  shippingSalesAmount: number;
  cancellationAmount: number;
  returnAmount: number;
  commissionAmount: number;
  adjustmentAmount: number;
  settlementAmount: number;
  ledgerEntryCount: number;
  status: AdminSettlementStatus;
  createdAt: string;
  confirmedAt: string | null;
}

export interface AdminSettlementPage {
  content: AdminSettlement[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface AdminSettlementLedger {
  id: number;
  sellerOrderId: number;
  type: AdminSettlementLedgerType;
  amount: number;
  currency: string;
  occurredAt: string;
  eligibleAt: string | null;
  reason: string | null;
  sourceType: AdminSettlementSourceType;
  sourceId: number;
  sourceDetailKey: string;
  commissionRateBps: number | null;
  commissionBaseAmount: number | null;
  adminUserId: number | null;
  reversalOfEntryId: number | null;
  createdAt: string;
}

export interface AdminSettlementDetail {
  settlement: AdminSettlement;
  holdReason: string | null;
  heldAt: string | null;
  heldByAdminUserId: number | null;
  holdReleasedAt: string | null;
  holdReleasedByAdminUserId: number | null;
  confirmedByAdminUserId: number | null;
  ledgerEntries: AdminSettlementLedger[];
}

export interface AdminSettlementGenerateRequest {
  sellerId: number;
  periodStart: string;
  periodEnd: string;
  cutoff: string;
}

export interface AdminSettlementGenerateResult {
  created: boolean;
  settlement: AdminSettlement | null;
}

export interface AdminSettlementSearchParams {
  sellerId?: number;
  status?: AdminSettlementStatus;
  periodStart?: string;
  periodEnd?: string;
  page?: number;
  size?: number;
}
