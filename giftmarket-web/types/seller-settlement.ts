export type SellerSettlementStatus = "READY" | "ON_HOLD" | "CONFIRMED";

export const SELLER_SETTLEMENT_STATUS_LABEL: Record<SellerSettlementStatus, string> = {
  READY: "정산 대기",
  ON_HOLD: "정산 보류",
  CONFIRMED: "정산 확정",
};

export type SellerSettlementLedgerType =
  | "SALE_PRODUCT"
  | "SALE_SHIPPING"
  | "CANCELLATION_REFUND"
  | "RETURN_REFUND"
  | "COMMISSION"
  | "COMMISSION_REVERSAL"
  | "MANUAL_ADJUSTMENT";

export const SELLER_SETTLEMENT_LEDGER_LABEL: Record<SellerSettlementLedgerType, string> = {
  SALE_PRODUCT: "상품 매출",
  SALE_SHIPPING: "배송비 매출",
  CANCELLATION_REFUND: "취소 환불",
  RETURN_REFUND: "반품 환불",
  COMMISSION: "판매 수수료",
  COMMISSION_REVERSAL: "수수료 환입",
  MANUAL_ADJUSTMENT: "정산 조정",
};

export interface SellerSettlement {
  id: number;
  settlementNumber: string;
  periodStart: string;
  periodEnd: string;
  currency: string;
  totalProductSalesAmount: number;
  totalShippingSalesAmount: number;
  totalCancellationAmount: number;
  totalReturnAmount: number;
  totalCommissionAmount: number;
  totalAdjustmentAmount: number;
  settlementAmount: number;
  ledgerEntryCount: number;
  status: SellerSettlementStatus;
  confirmedAt: string | null;
  createdAt: string;
}

export interface SellerSettlementPage {
  settlements: SellerSettlement[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface SellerSettlementLedger {
  id: number;
  sellerOrderId: number;
  type: SellerSettlementLedgerType;
  amount: number;
  currency: string;
  occurredAt: string;
  eligibleAt: string | null;
  reason: string | null;
}

export interface SellerSettlementDetail {
  settlement: SellerSettlement;
  ledgerEntries: SellerSettlementLedger[];
}

export interface SellerSettlementSummary {
  currency: string;
  unassignedAmount: number;
  eligibleAmount: number;
  holdAmount: number;
  awaitingEligibilityAmount: number;
}
